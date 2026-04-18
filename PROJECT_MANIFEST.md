# PROJECT MANIFEST — SaaS Nexus Inventory Engine

> **Single source of truth** for project state. Must be updated with every commit.

---

## VERSION: 0.3.0

**Last Updated:** 2026-04-19
**Phase:** Phase 1 Complete ✅ → Phase 2 Ready

---

## CURRENT STATE

| Field             | Value                                                              |
|-------------------|--------------------------------------------------------------------|
| Objective         | Production-grade Multi-Tenant Inventory Engine with Agentic AI     |
| Active Sprint     | Phase 2: Business Logic & Physical Integration                     |
| Tech Stack        | Java 21 (Virtual Threads), Spring Boot 3.4, PostgreSQL 16, RLS, ZXing, Spring AI |
| Current Blockers  | None. Phase 1 (Architecture & Security) is 100% verified via curl  |
| Git Remote        | https://github.com/sarkarsagnik-commits/saas-nexus-inventory.git   |

---

## ARCHITECTURE DECISIONS LOG

### ADR-001: Database-Level Tenant Isolation via RLS
- **Decision:** Use PostgreSQL Row-Level Security (RLS) instead of manual `WHERE tenant_id = ?` in every repository.
- **Rationale:** Eliminates developer error — impossible to accidentally leak cross-tenant data.
- **Status:** ✅ Verified

### ADR-002: Transaction-Scoped Tenant Context (STRICT — is_local=true)
- **Decision:** Use `SELECT set_config('app.current_tenant', ?, true)` — **transaction-local** scoping.
- **Rationale:** Transaction-local settings auto-revert on COMMIT/ROLLBACK, making it impossible for a pooled connection to retain a stale tenant from a previous request. This is the strictest isolation model.
- **Prerequisite:** Requires `spring.datasource.hikari.auto-commit=false` so connections are already in a transaction block when `getConnection()` returns. Without this, `is_local=true` is silently discarded.
- **Leak Prevention:** `set_config` is called on **every** `getConnection()` — resetting to empty string `""` when no tenant is present. This provides defense-in-depth.
- **⚠️ SECURITY NOTE:** Using `is_local=false` (session-scoped) was attempted in v0.2.0 and **reverted** as a security regression. Session-scoped settings persist on pooled connections, violating strict data isolation.
- **Status:** ✅ Reverted & Verified in v0.2.1

### ADR-003: Non-Superuser Application Role (app_user)
- **Decision:** The Spring Boot app connects as `app_user` (non-superuser), NOT `inventory_user` (superuser).
- **Rationale:** PostgreSQL superusers bypass **all** RLS policies regardless of `FORCE ROW LEVEL SECURITY`. The `app_user` role has `GRANT SELECT, INSERT, UPDATE, DELETE` with no superuser privileges, making it fully subject to RLS enforcement.
- **Status:** ✅ Fixed & Verified

### ADR-004: Virtual Thread Compatibility
- **Decision:** `spring.threads.virtual.enabled=true` with stateless session management (`STATELESS`), no `synchronized` blocks, `ThreadLocal`-based tenant context.
- **Rationale:** Avoids carrier thread pinning. Spring Boot 3.4 propagates `ThreadLocal` correctly across virtual threads.
- **Status:** ✅ Active

### ADR-005: Soft Deletes via @SQLRestriction
- **Decision:** Products use `deleted_at` column with Hibernate 6's `@SQLRestriction("deleted_at IS NULL")`.
- **Rationale:** Replaces deprecated `@Where`. Hibernate automatically appends the filter to all queries.
- **Status:** ✅ Active

### ADR-006: HikariCP auto-commit=false (Programmatic — NOT via properties)
- **Decision:** Set `hikariDataSource.setAutoCommit(false)` **programmatically** in `DataSourceConfig.java`.
- **Rationale:** `DataSourceProperties.initializeDataSourceBuilder()` ignores all `spring.datasource.hikari.*` properties. Setting `spring.datasource.hikari.auto-commit=false` in `application.properties` has NO effect when using a custom `@Primary DataSource` bean. The auto-commit must be set directly on the `HikariDataSource` instance before wrapping it with `TenantAwareDataSource`.
- **Why required:** HikariCP defaults to `autoCommit=true`. With auto-commit on, `getConnection()` returns a connection outside any transaction block, causing `set_config(..., true)` to be silently discarded.
- **Status:** ✅ Root cause fixed in v0.3.0

---

## KNOWLEDGE BASE

| Topic                  | Detail                                                                        |
|------------------------|-------------------------------------------------------------------------------|
| Security Architecture  | Shared database, RLS-enforced tenant isolation                                |
| Tenant Context Flow    | `X-Tenant-ID` header → `TenantFilter` → `TenantContext` (ThreadLocal) → `TenantAwareDataSource` → `set_config(..., true)` (transaction-local) → RLS policy |
| DB Helper Function     | `current_tenant_id()` reads `current_setting('app.current_tenant', true)::UUID` |
| Seed Tenants           | Acme Corp: `a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11` / Globex Inc: `b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22` |
| Data Lifecycle         | Soft deletes via `deleted_at IS NULL` (entity + query level)                  |
| JPA Auditing           | `@CreatedDate`, `@LastModifiedDate`, `@CreatedBy` via `JpaAuditingConfig`     |
| Phase 2 Auth Upgrade   | Replace `X-Tenant-ID` header with JWT `tenant_id` claim extraction            |

---

## FILE MANIFEST

```
src/main/java/com/saasnexus/inventory/
├── config/
│   ├── DataSourceConfig.java          # Wires TenantAwareDataSource as primary DataSource
│   ├── JpaAuditingConfig.java         # Enables @CreatedDate/@CreatedBy auto-population
│   ├── SecurityConfig.java            # Phase 1: stateless, CSRF-off, permit-all
│   └── TenantAwareDataSource.java     # DataSource proxy → set_config() on every connection
├── controller/
│   └── ProductController.java         # GET /api/products (RLS-filtered, no manual WHERE)
├── model/
│   └── Product.java                   # JPA entity with soft delete + auditing
├── repository/
│   └── ProductRepository.java         # JpaRepository<Product, UUID> — RLS-transparent
├── service/                           # (Phase 2)
├── tenant/
│   ├── TenantContext.java             # ThreadLocal tenant ID holder (virtual-thread safe)
│   ├── TenantFilter.java              # Servlet filter: X-Tenant-ID → TenantContext
│   └── TenantConnectionInterceptor.java # Hibernate StatementInspector (audit logging)
└── InventoryApplication.java          # Spring Boot entry point

src/main/resources/
├── application.properties             # DB=app_user, virtual threads, hikari auto-commit=false
└── db/init/
    ├── 01-rls-setup.sql               # Schema + RLS policies + app_user role + seed tenants
    └── 02-seed-products.sql           # 6 seed products (3 per tenant)
```

---

## TODO LIST

- [x] Phase 1: Scaffold Spring Boot 3.4 & PostgreSQL 16 container
- [x] Phase 1: Implement RLS init SQL (tenants, users, products, stock_ledger)
- [x] Phase 1: TenantContext (ThreadLocal) + TenantFilter (X-Tenant-ID header)
- [x] Phase 1: TenantAwareDataSource (set_config proxy) + DataSourceConfig
- [x] Phase 1: SecurityConfig (stateless, permit-all for Phase 1)
- [x] Phase 1: Hibernate StatementInspector (audit logging)
- [x] Phase 1: Product entity, repository, controller boilerplate
- [x] Phase 1: Verify RLS isolation via curl (Tenant A ↔ Tenant B) ✅
- [ ] Phase 2: Integrate ZXing for barcode/QR Code generation
- [ ] Phase 2: Implement Virtual Thread Concurrency Benchmark (2k RPS target)
- [ ] Phase 2: CRUD endpoints for Product management (POST, PUT, DELETE)
- [ ] Phase 2: Stock Ledger service (INBOUND, OUTBOUND, CORRECTION, RETURN)
- [ ] Phase 3: Spring AI Agentic Integration (Function Calling / Tools)
- [ ] Phase 3: JWT-based authentication (replace X-Tenant-ID header)

---

## VERIFICATION LOG

| Date       | Test                                  | Result |
|------------|---------------------------------------|--------|
| 2026-04-19 | `curl Tenant A → 3 Acme products`    | ✅ PASS |
| 2026-04-19 | `curl Tenant B → 3 Globex products`  | ✅ PASS |
| 2026-04-19 | No cross-tenant data leakage         | ✅ PASS |
| 2026-04-19 | `app_user` bypasses RLS?             | ✅ NO (non-superuser) |
| 2026-04-19 | Revert session-scoped → transaction-local | ✅ SECURITY FIX (v0.2.1) |
| 2026-04-19 | DataSourceConfig: programmatic autoCommit=false | ✅ ROOT CAUSE FIX (v0.3.0) |