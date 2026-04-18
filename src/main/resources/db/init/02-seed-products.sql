-- ================================================================
-- 02-seed-products.sql
-- Seed products for RLS isolation verification
-- ================================================================
-- Run this AFTER 01-rls-setup.sql has executed.
-- Can be executed via: docker exec -i saasnexus-postgres psql -U inventory_user -d inventory_db < 02-seed-products.sql
--
-- Tenant A (Acme Corp):   a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11
-- Tenant B (Globex Inc):  b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22
-- ================================================================

-- Must set tenant context before inserts (RLS WITH CHECK requires it)
SET app.current_tenant = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11';

INSERT INTO products (tenant_id, name, sku, description, category, quantity, reorder_point, price)
VALUES
    ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'Wireless Mouse',       'ACME-WM-001', 'Ergonomic wireless mouse with USB-C receiver',  'Electronics', 150, 20, 29.99),
    ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'Mechanical Keyboard',  'ACME-KB-002', 'Cherry MX Brown switches, full-size layout',     'Electronics',  75, 15, 89.99),
    ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'USB-C Hub',            'ACME-HB-003', '7-port USB-C hub with 4K HDMI and PD charging',  'Accessories',  200, 30, 49.99)
ON CONFLICT (tenant_id, sku) DO NOTHING;

SET app.current_tenant = 'b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22';

INSERT INTO products (tenant_id, name, sku, description, category, quantity, reorder_point, price)
VALUES
    ('b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22', 'Standing Desk',        'GLX-SD-001',  'Electric sit-stand desk, 60x30 inches',          'Furniture',    40, 10, 499.99),
    ('b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22', 'Monitor Arm',          'GLX-MA-002',  'Dual monitor arm, VESA 75x75 and 100x100',       'Furniture',    90, 20, 129.99),
    ('b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22', 'Desk Lamp',            'GLX-DL-003',  'LED desk lamp with wireless charging base',       'Lighting',     120, 25, 59.99)
ON CONFLICT (tenant_id, sku) DO NOTHING;

-- Reset tenant context
RESET app.current_tenant;

-- Verify: count per tenant (run as superuser to bypass RLS)
SELECT t.name AS tenant, COUNT(p.id) AS product_count
FROM products p
JOIN tenants t ON t.id = p.tenant_id
GROUP BY t.name
ORDER BY t.name;
