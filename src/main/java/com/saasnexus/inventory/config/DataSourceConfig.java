package com.saasnexus.inventory.config;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Wires the {@link TenantAwareDataSource} as the primary {@link DataSource}.
 *
 * <p>Spring Boot auto-configures a HikariCP DataSource from
 * {@code application.properties}. This config wraps that DataSource
 * so that every connection checkout executes
 * {@code SET LOCAL app.current_tenant = ?} before any SQL runs.</p>
 *
 * @see TenantAwareDataSource
 */
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource(DataSourceProperties properties) {
        DataSource hikariDataSource = properties
                .initializeDataSourceBuilder()
                .build();
        return new TenantAwareDataSource(hikariDataSource);
    }
}
