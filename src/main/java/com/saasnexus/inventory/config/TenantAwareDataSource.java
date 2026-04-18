package com.saasnexus.inventory.config;

import com.saasnexus.inventory.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Wraps the underlying {@link DataSource} to inject
 * {@code SET LOCAL app.current_tenant} on every connection checkout
 * via PostgreSQL's {@code set_config()} function.
 *
 * <h3>Transaction-Scoped Tenant Context (STRICT)</h3>
 * <p>Uses {@code set_config('app.current_tenant', ?, true)} where
 * {@code is_local = true} ensures the tenant variable is scoped
 * <b>strictly to the current transaction</b>. When the transaction
 * commits or rolls back, PostgreSQL automatically reverts the setting
 * — making it impossible for a pooled connection to retain a stale
 * tenant from a previous request.</p>
 *
 * <h3>Why this is safe</h3>
 * <p>HikariCP is configured with {@code auto-commit=false}
 * (via {@code spring.datasource.hikari.auto-commit=false}). This
 * guarantees that every connection checked out from the pool is
 * already inside a transaction block, so {@code is_local = true}
 * works correctly. Without this setting, the {@code set_config}
 * call would execute in an auto-committed single-statement
 * transaction and be silently discarded.</p>
 *
 * <h3>Leak Prevention</h3>
 * <p>This proxy <b>always</b> calls {@code set_config} — setting
 * the tenant to an empty string when no {@link TenantContext} is
 * present. Combined with transaction-local scoping, this provides
 * defense-in-depth against tenant leakage.</p>
 *
 * <h3>Virtual Thread Safety</h3>
 * <p>Fully compatible with Java 21 Virtual Threads. Each virtual
 * thread gets its own {@link TenantContext} via {@code ThreadLocal},
 * and each connection checkout sets the tenant independently.
 * No synchronized blocks.</p>
 *
 * @see TenantContext
 * @see DataSourceConfig
 */
public class TenantAwareDataSource extends DelegatingDataSource {

    private static final Logger log = LoggerFactory.getLogger(TenantAwareDataSource.class);

    /**
     * Uses {@code set_config(setting_name, new_value, is_local)}.
     * <ul>
     *   <li>{@code is_local = true} → transaction-scoped (auto-reverts on COMMIT/ROLLBACK)</li>
     *   <li>We use {@code set_config()} instead of {@code SET LOCAL} because
     *       PostgreSQL does not support parameterized bindings ({@code ?})
     *       in {@code SET} statements.</li>
     *   <li>Requires {@code spring.datasource.hikari.auto-commit=false}
     *       so the connection is already in a transaction when this executes.</li>
     * </ul>
     */
    private static final String SET_TENANT_SQL =
            "SELECT set_config('app.current_tenant', ?, true)";

    public TenantAwareDataSource(DataSource targetDataSource) {
        super(targetDataSource);
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection connection = super.getConnection();
        applyTenantContext(connection);
        return connection;
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Connection connection = super.getConnection(username, password);
        applyTenantContext(connection);
        return connection;
    }

    /**
     * Always sets {@code app.current_tenant} on the connection — either to
     * the real tenant ID or to an empty string. This guarantees:
     * <ol>
     *   <li>RLS sees the correct tenant for authenticated requests.</li>
     *   <li>Transaction-local scoping auto-clears on COMMIT/ROLLBACK.</li>
     *   <li>Empty-string fallback provides defense-in-depth for no-tenant requests.</li>
     * </ol>
     *
     * <p>Uses a {@link PreparedStatement} with a parameterized query to
     * guard against SQL injection.</p>
     *
     * @param connection the JDBC connection to configure
     * @throws SQLException if the set_config statement fails
     */
    private void applyTenantContext(Connection connection) throws SQLException {
        String tenantId = TenantContext.getTenantId().orElse("");
        try (PreparedStatement ps = connection.prepareStatement(SET_TENANT_SQL)) {
            ps.setString(1, tenantId);
            ps.execute();

            if (log.isDebugEnabled()) {
                log.debug("Tenant context set on connection (transaction-local): tenant_id={}",
                        tenantId.isEmpty() ? "NONE" : tenantId);
            }
        } catch (SQLException e) {
            throw new TenantContextException(
                    "Failed to set tenant context [" + tenantId + "] on JDBC connection", e);
        }
    }

    /**
     * Dedicated exception for tenant context propagation failures.
     * Extends {@link RuntimeException} so it propagates through the
     * Spring transaction infrastructure cleanly.
     */
    public static class TenantContextException extends RuntimeException {
        public TenantContextException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
