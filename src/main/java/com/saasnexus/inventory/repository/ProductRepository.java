package com.saasnexus.inventory.repository;

import com.saasnexus.inventory.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Product} entities.
 *
 * <h3>Multi-Tenancy</h3>
 * <p>No {@code WHERE tenant_id = ?} is needed in any query method.
 * PostgreSQL Row-Level Security (RLS) automatically filters rows
 * based on the {@code app.current_tenant} session variable set by
 * {@link com.saasnexus.inventory.config.TenantAwareDataSource}.</p>
 *
 * <h3>Soft Delete</h3>
 * <p>The {@link Product} entity uses {@code @SQLRestriction("deleted_at IS NULL")}
 * so all standard queries automatically exclude soft-deleted rows.</p>
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
    // RLS handles tenant filtering at the database level.
    // Standard JpaRepository methods (findAll, findById, save, etc.)
    // work correctly without any tenant-specific modifications.
}
