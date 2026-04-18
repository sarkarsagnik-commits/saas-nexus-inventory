package com.saasnexus.inventory.config;

import com.zaxxer.hikari.HikariDataSource;
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
 * {@code set_config('app.current_tenant', ?, true)} on the JDBC connection,
 * enforcing transaction-scoped tenant isolation via PostgreSQL RLS.</p>
 *
 * <h3>HikariCP auto-commit=false</h3>
 * <p>{@code autoCommit} is explicitly set to {@code false} here because
 * {@link DataSourceProperties#initializeDataSourceBuilder()} does NOT
 * apply {@code spring.datasource.hikari.*} properties. Without this,
 * HikariCP defaults to {@code autoCommit=true}, and
 * {@code set_config(..., true)} (transaction-local) is silently
 * discarded before the real Spring transaction begins.</p>
 *
 * @see TenantAwareDataSource
 */
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource(DataSourceProperties properties) {
        HikariDataSource hikariDataSource = properties
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();

        // CRITICAL: Must be set here because initializeDataSourceBuilder()
        // does NOT read spring.datasource.hikari.* properties.
        // Without this, autoCommit=true causes set_config(..., true)
        // to be silently discarded (not inside a transaction block).
        hikariDataSource.setAutoCommit(false);

        return new TenantAwareDataSource(hikariDataSource);
    }
}
