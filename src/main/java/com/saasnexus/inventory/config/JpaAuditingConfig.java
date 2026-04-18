package com.saasnexus.inventory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

/**
 * Enables JPA Auditing for automatic population of
 * {@code @CreatedDate}, {@code @LastModifiedDate},
 * {@code @CreatedBy}, and {@code @LastModifiedBy} fields.
 *
 * <h3>Phase 2 Upgrade</h3>
 * <p>Replace the hardcoded "system" auditor with the authenticated
 * user's email extracted from the JWT {@code SecurityContextHolder}.</p>
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<String> auditorProvider() {
        // Phase 1: hardcoded auditor for development
        // Phase 2: extract from SecurityContextHolder.getContext()
        //          .getAuthentication().getName()
        return () -> Optional.of("system");
    }
}
