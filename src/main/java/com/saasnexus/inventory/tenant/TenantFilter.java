package com.saasnexus.inventory.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that extracts the tenant identifier from the incoming
 * HTTP request and stores it in {@link TenantContext} for the duration
 * of the request lifecycle.
 *
 * <h3>Current behaviour (Phase 1)</h3>
 * <p>Reads the tenant UUID from the {@code X-Tenant-ID} request header.
 * This is suitable for development and Postman/curl testing.</p>
 *
 * <h3>Phase 2 upgrade</h3>
 * <p>Replace the header extraction with JWT {@code tenant_id} claim
 * extraction from {@code SecurityContextHolder} once Spring Security
 * JWT resource-server is configured.</p>
 *
 * <h3>Virtual Thread Safety</h3>
 * <p>This filter is fully compatible with virtual threads. It avoids
 * {@code synchronized} blocks and relies solely on {@link TenantContext}'s
 * {@code ThreadLocal} which Spring Boot 3.4+ propagates correctly
 * across virtual threads.</p>
 *
 * @see TenantContext
 */
@Component
@Order(1)  // run before Spring Security filters
public class TenantFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantFilter.class);

    /**
     * HTTP header name carrying the tenant UUID.
     * Phase 2: Replace with JWT claim extraction.
     */
    private static final String TENANT_HEADER = "X-Tenant-ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
            throws ServletException, IOException {

        String tenantId = request.getHeader(TENANT_HEADER);

        if (tenantId != null && !tenantId.isBlank()) {
            String sanitized = tenantId.trim();
            TenantContext.setTenantId(sanitized);

            if (log.isDebugEnabled()) {
                log.debug("Tenant context set from header: tenant_id={}, uri={}",
                        sanitized, request.getRequestURI());
            }
        } else {
            log.trace("No {} header present on request: {}",
                    TENANT_HEADER, request.getRequestURI());
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // CRITICAL: always clear to prevent tenant leakage
            // between requests on the same thread/virtual-thread
            TenantContext.clear();
        }
    }
}
