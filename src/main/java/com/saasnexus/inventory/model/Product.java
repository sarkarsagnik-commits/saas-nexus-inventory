package com.saasnexus.inventory.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapped to the {@code products} table.
 *
 * <h3>Multi-Tenancy</h3>
 * <p>The {@code tenant_id} column is populated by the application when
 * creating products. Row-Level Security (RLS) in PostgreSQL automatically
 * filters results — no manual {@code WHERE tenant_id = ?} is needed
 * in any repository query.</p>
 *
 * <h3>Soft Delete</h3>
 * <p>Products are never physically deleted. The {@code deleted_at} column
 * marks a product as logically removed. The {@link SQLRestriction} ensures
 * Hibernate automatically adds {@code deleted_at IS NULL} to all queries.</p>
 *
 * <h3>Auditing</h3>
 * <p>JPA auditing fills {@code createdBy}, {@code updatedBy},
 * {@code createdAt}, and {@code updatedAt} automatically via
 * {@link AuditingEntityListener}.</p>
 */
@Entity
@Table(name = "products")
@SQLRestriction("deleted_at IS NULL")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "sku", nullable = false, length = 100)
    private String sku;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "quantity", nullable = false)
    @Builder.Default
    private Integer quantity = 0;

    @Column(name = "reorder_point", nullable = false)
    @Builder.Default
    private Integer reorderPoint = 10;

    @Column(name = "price", precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "barcode_data", columnDefinition = "TEXT")
    private String barcodeData;

    // --- Auditing Fields ---

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private String updatedBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
