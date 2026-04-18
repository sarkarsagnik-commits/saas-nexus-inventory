package com.saasnexus.inventory.controller;

import com.saasnexus.inventory.model.Product;
import com.saasnexus.inventory.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for the {@code /api/products} endpoint.
 *
 * <h3>RLS Verification</h3>
 * <p>The {@code GET /api/products} endpoint returns <strong>only</strong>
 * the products belonging to the tenant identified by the
 * {@code X-Tenant-ID} request header. No manual filtering is performed
 * in code — PostgreSQL RLS enforces isolation automatically.</p>
 *
 * <h3>Virtual Threads</h3>
 * <p>With {@code spring.threads.virtual.enabled=true}, each request
 * is handled by a virtual thread. This controller uses standard
 * blocking-style code (no reactive patterns needed) — the JVM
 * unmounts the virtual thread during I/O waits automatically.</p>
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);

    private final ProductRepository productRepository;

    public ProductController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * Returns all products visible to the current tenant.
     *
     * <p>RLS ensures this only returns rows where
     * {@code tenant_id = current_setting('app.current_tenant')}.</p>
     *
     * @return 200 OK with the list of tenant-scoped products
     */
    @GetMapping
    public ResponseEntity<List<Product>> getAllProducts() {
        log.debug("GET /api/products — fetching tenant-scoped products (thread: {})",
                Thread.currentThread());

        List<Product> products = productRepository.findAll();

        log.debug("Returning {} products", products.size());
        return ResponseEntity.ok(products);
    }
}
