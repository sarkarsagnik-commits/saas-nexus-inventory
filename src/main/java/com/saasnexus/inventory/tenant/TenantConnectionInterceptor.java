package com.saasnexus.inventory.tenant;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hibernate {@link StatementInspector} that logs the active tenant context
 * alongside every SQL statement for audit and debugging purposes.
 *
 * <h3>Design Note</h3>
 * <p>The actual tenant injection happens at the DataSource level via
 * {@link com.saasnexus.inventory.config.TenantAwareDataSource}, which
 * executes {@code SET LOCAL app.current_tenant = ?} on every connection
 * checkout. This inspector is <strong>not</strong> responsible for setting
 * the tenant — it exists purely for observability.</p>
 *
 * <h3>Activation</h3>
 * <p>Register in {@code application.properties}:</p>
 * <pre>
 * spring.jpa.properties.hibernate.session_factory.statement_inspector=\
 *     com.saasnexus.inventory.tenant.TenantConnectionInterceptor
 * </pre>
 *
 * <h3>Virtual Thread Safety</h3>
 * <p>This class is stateless and reads from {@link TenantContext} which
 * uses {@code ThreadLocal} — fully safe for virtual threads.</p>
 *
 * @see com.saasnexus.inventory.config.TenantAwareDataSource
 */
public class TenantConnectionInterceptor implements StatementInspector {

    private static final Logger log = LoggerFactory.getLogger(TenantConnectionInterceptor.class);

    @Override
    public String inspect(String sql) {
        if (log.isTraceEnabled()) {
            String tenantId = TenantContext.getTenantId().orElse("NONE");
            log.trace("[tenant={}] SQL: {}", tenantId, sql);
        }
        // Return the SQL unmodified — tenant enforcement is at the DataSource level
        return sql;
    }
}
