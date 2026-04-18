package com.saasnexus.inventory.tenant;

import java.util.Optional;

/**
 * Thread-safe holder for the current tenant ID during a request lifecycle.
 *
 * <h3>How it works</h3>
 * <ol>
 *   <li>A servlet filter / interceptor extracts the tenant ID from the
 *       JWT {@code tenant_id} claim (or {@code X-Tenant-ID} header in dev).</li>
 *   <li>It calls {@link #setTenantId(String)} to store the value.</li>
 *   <li>{@link com.saasnexus.inventory.config.TenantAwareDataSource}
 *       reads it via {@link #getTenantId()} and executes
 *       {@code set_config('app.current_tenant', ?, true)} on the JDBC
 *       connection, scoping the tenant to the current transaction.</li>
 *   <li>The filter calls {@link #clear()} in a {@code finally} block
 *       to prevent tenant leakage across requests.</li>
 * </ol>
 *
 * <h3>Virtual Thread Compatibility</h3>
 * <p>Spring Boot 3.4+ propagates {@link ThreadLocal} values across
 * virtual threads. Once {@code ScopedValue} is finalized (Java 25+),
 * this class should be migrated for better performance.</p>
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
        // utility class — prevent instantiation
    }

    /**
     * Sets the tenant ID for the current thread/virtual-thread.
     *
     * @param tenantId the UUID string of the current tenant
     */
    public static void setTenantId(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    /**
     * Returns the tenant ID if one has been set for this request.
     *
     * @return an Optional containing the tenant ID, or empty if not set
     */
    public static Optional<String> getTenantId() {
        return Optional.ofNullable(CURRENT_TENANT.get());
    }

    /**
     * Returns the tenant ID or throws if none is available.
     * Use this in code paths that <em>require</em> a tenant context.
     *
     * @return the current tenant ID
     * @throws IllegalStateException if no tenant context is set
     */
    public static String requireTenantId() {
        return getTenantId()
                .orElseThrow(() -> new IllegalStateException(
                        "No tenant context available. "
                        + "Ensure the request passes through TenantFilter "
                        + "with a valid X-Tenant-ID header or JWT tenant_id claim."));
    }

    /**
     * Clears the tenant context. <strong>Must</strong> be called in a
     * {@code finally} block at the end of every request to prevent
     * tenant data leakage.
     */
    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
