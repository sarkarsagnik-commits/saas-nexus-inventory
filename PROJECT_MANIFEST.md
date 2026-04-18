VERSION: 0.1.0
CURRENT STATE
Objective: Production-grade Multi-Tenant Inventory Engine with Agentic AI.

Active Sprint: Phase 2: Business Logic & Physical Integration.

Tech Stack: Java 21 (Virtual Threads), Spring Boot 3.4, PostgreSQL 16, RLS, ZXing, Git/GitHub.

Current Blockers: None. Phase 1 (Architecture & Security) is 100% verified.

KNOWLEDGE BASE
Security Architecture: Shared database using PostgreSQL Row-Level Security (RLS).

Core Logic: Transaction-scoped tenant context managed via TenantAwareDataSource using SET LOCAL app.current_tenant.

Data Lifecycle: Soft Deletes implemented (deleted_at IS NULL filter verified in logs).

Git State: Local repo initialized and pushed to https://github.com/sarkarsagnik-commits/saas-nexus-inventory.git.

TODO LIST
[x] Phase 1: Scaffold Spring Boot 3.4 & PostgreSQL 16 container.

[x] Phase 1: Implement & Verify RLS Isolation (Verified via curl).

[ ] Phase 2: Integrate ZXing for QR Code generation.

[ ] Phase 2: Implement Virtual Thread Concurrency Benchmark (2k RPS).

[ ] Phase 3: Spring AI Agentic Integration (Function Calling).