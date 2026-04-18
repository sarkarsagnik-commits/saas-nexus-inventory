package com.saasnexus.inventory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Phase 1 security configuration — permits all requests and disables
 * CSRF for stateless REST API development.
 *
 * <h3>Phase 2 Upgrade Path</h3>
 * <ul>
 *   <li>Configure as an OAuth2 Resource Server with JWT validation.</li>
 *   <li>Extract {@code tenant_id} from JWT claims instead of
 *       the {@code X-Tenant-ID} header.</li>
 *   <li>Add role-based access control per endpoint.</li>
 * </ul>
 *
 * <h3>Virtual Thread Compatibility</h3>
 * <p>Stateless session management ({@code STATELESS}) ensures no
 * HTTP sessions are created, which avoids session-scoped locks
 * that could pin virtual threads to carrier threads.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF — stateless REST API
            .csrf(AbstractHttpConfigurer::disable)

            // No HTTP sessions — critical for virtual thread efficiency
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Disable form login and HTTP Basic (no browser-based auth)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)

            // Phase 1: permit all requests
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            );

        return http.build();
    }
}
