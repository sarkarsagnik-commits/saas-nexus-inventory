-- ================================================================
-- 01-rls-setup.sql
-- SaaS Nexus Inventory — PostgreSQL Row-Level Security Bootstrap
-- ================================================================
-- Runs automatically on first container start via
-- docker-entrypoint-initdb.d mount.
--
-- Design decisions:
--   • Every tenant-scoped index uses tenant_id as the LEADING
--     column so RLS evaluation stays sub-millisecond.
--   • A helper function current_tenant_id() reads the session
--     variable app.current_tenant to keep policies DRY.
--   • FORCE ROW LEVEL SECURITY ensures even table owners
--     cannot bypass isolation.
--   • Soft-delete support via deleted_at column on products.
-- ================================================================

-- -----------------------------------------------------------------
-- 0. Extensions
-- -----------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS "pgcrypto";   -- gen_random_uuid()

-- -----------------------------------------------------------------
-- 1. Helper function — reads the session variable once per query
-- -----------------------------------------------------------------
CREATE OR REPLACE FUNCTION current_tenant_id()
RETURNS UUID
LANGUAGE sql
STABLE            -- result is constant within a single statement
PARALLEL SAFE
AS $$
    SELECT current_setting('app.current_tenant', true)::UUID;
$$;

COMMENT ON FUNCTION current_tenant_id()
    IS 'Returns the tenant UUID stored in the session variable app.current_tenant. '
       'Used by every RLS policy to enforce data isolation.';

-- -----------------------------------------------------------------
-- 2. Tenants table (master table — NO RLS on this table)
-- -----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tenants (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255)    NOT NULL,
    slug            VARCHAR(100)    NOT NULL UNIQUE,
    contact_email   VARCHAR(255),
    plan            VARCHAR(50)     NOT NULL DEFAULT 'free',
    is_active       BOOLEAN         NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_tenants_slug ON tenants(slug);

COMMENT ON TABLE tenants IS 'Master table of tenant organizations. Not subject to RLS.';

-- -----------------------------------------------------------------
-- 3. Users table (tenant-scoped)
-- -----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    email           VARCHAR(255)    NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    full_name       VARCHAR(255),
    role            VARCHAR(50)     NOT NULL DEFAULT 'MEMBER',
    is_active       BOOLEAN         NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT uq_users_tenant_email UNIQUE (tenant_id, email)
);

-- tenant_id is the LEADING column in every index for RLS perf
CREATE INDEX idx_users_tenant_id        ON users(tenant_id);
CREATE INDEX idx_users_tenant_role      ON users(tenant_id, role);

ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE users FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON users
    USING       (tenant_id = current_tenant_id())
    WITH CHECK  (tenant_id = current_tenant_id());

-- -----------------------------------------------------------------
-- 4. Products table (tenant-scoped, soft-delete)
-- -----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS products (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name            VARCHAR(255)    NOT NULL,
    sku             VARCHAR(100)    NOT NULL,
    description     TEXT,
    category        VARCHAR(100),
    quantity        INT             NOT NULL DEFAULT 0,
    reorder_point   INT             NOT NULL DEFAULT 10,
    price           NUMERIC(12, 2),
    barcode_data    TEXT,
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ,                           -- soft delete

    CONSTRAINT uq_products_tenant_sku UNIQUE (tenant_id, sku)
);

-- tenant_id is the LEADING column in every index for RLS perf
CREATE INDEX idx_products_tenant_id           ON products(tenant_id);
CREATE INDEX idx_products_tenant_category     ON products(tenant_id, category);
CREATE INDEX idx_products_tenant_sku          ON products(tenant_id, sku);
CREATE INDEX idx_products_tenant_reorder      ON products(tenant_id, reorder_point)
    WHERE deleted_at IS NULL;

ALTER TABLE products ENABLE ROW LEVEL SECURITY;
ALTER TABLE products FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON products
    USING       (tenant_id = current_tenant_id())
    WITH CHECK  (tenant_id = current_tenant_id());

-- -----------------------------------------------------------------
-- 5. Stock Ledger table (tenant-scoped, append-only audit trail)
-- -----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS stock_ledger (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    product_id      UUID            NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    adjustment_type VARCHAR(30)     NOT NULL,     -- 'INBOUND', 'OUTBOUND', 'CORRECTION', 'RETURN'
    quantity_change INT             NOT NULL,      -- positive = in, negative = out
    running_total   INT             NOT NULL,      -- product quantity AFTER this adjustment
    reference_id    VARCHAR(255),                  -- PO number, order ID, etc.
    notes           TEXT,
    created_by      VARCHAR(255),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT chk_adjustment_type CHECK (
        adjustment_type IN ('INBOUND', 'OUTBOUND', 'CORRECTION', 'RETURN')
    )
);

-- tenant_id is the LEADING column in every index for RLS perf
CREATE INDEX idx_ledger_tenant_id             ON stock_ledger(tenant_id);
CREATE INDEX idx_ledger_tenant_product        ON stock_ledger(tenant_id, product_id);
CREATE INDEX idx_ledger_tenant_created        ON stock_ledger(tenant_id, created_at DESC);
CREATE INDEX idx_ledger_tenant_product_time   ON stock_ledger(tenant_id, product_id, created_at DESC);

ALTER TABLE stock_ledger ENABLE ROW LEVEL SECURITY;
ALTER TABLE stock_ledger FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON stock_ledger
    USING       (tenant_id = current_tenant_id())
    WITH CHECK  (tenant_id = current_tenant_id());

-- -----------------------------------------------------------------
-- 6. Application role with restricted privileges
-- -----------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'app_user') THEN
        CREATE ROLE app_user WITH LOGIN PASSWORD 'inventory_secret';
    END IF;
END
$$;

GRANT USAGE  ON SCHEMA public TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO app_user;
GRANT ALL    ON ALL SEQUENCES IN SCHEMA public TO app_user;

-- Future tables created in this schema also get the same grants
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES    TO app_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT ALL                            ON SEQUENCES TO app_user;

-- -----------------------------------------------------------------
-- 7. Seed data — two demo tenants for development
-- -----------------------------------------------------------------
INSERT INTO tenants (id, name, slug, contact_email, plan)
VALUES
    ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'Acme Corp',     'acme-corp',     'admin@acme.example.com',     'pro'),
    ('b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22', 'Globex Inc',    'globex-inc',    'admin@globex.example.com',   'free')
ON CONFLICT (slug) DO NOTHING;

-- -----------------------------------------------------------------
-- Done.
-- -----------------------------------------------------------------
