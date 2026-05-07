## Exploration: Sales Module (Módulo de Ventas)

### Current State
The system currently has 4 completed modules (Products, Bulk Products, Production Batches, Auth JWT). The database schema (V1__Initial_Schema.sql) **ALREADY HAS** the `sales` and `sale_items` tables created.

**Key findings:**
- Tables exist: `sales` (id, total_amount, created_at) and `sale_items` (id, sale_id, production_batch_id, quantity, unit_price_at_sale, unit_cost_at_sale)
- ProductionBatch entity has `currentStock` field for FIFO deduction
- ProductionBatchRepository has FIFO method: `findByProductIdAndCurrentStockGreaterThanOrderByProductionDateAsc()`
- SecurityConfig currently permits all to `/api/v1/product/**`, others need auth
- Standard pattern per module: domain → repository → service → controller → dto → mapper

### Affected Areas
- `src/main/java/com/mundolimpio/application/` — Need new `sales/` module folder
- `src/main/java/com/mundolimpio/application/sales/domain/Sale.java` — New JPA entity
- `src/main/java/com/mundolimpio/application/sales/domain/SaleItem.java` — New JPA entity (child of Sale)
- `src/main/java/com/mundolimpio/application/sales/repository/SaleRepository.java` — Spring Data JPA
- `src/main/java/com/mundolimpio/application/sales/repository/SaleItemRepository.java` — Spring Data JPA
- `src/main/java/com/mundolimpio/application/sales/service/SaleService.java` — Business logic + FIFO
- `src/main/java/com/mundolimpio/application/sales/controller/SaleController.java` — REST endpoints
- `src/main/java/com/mundolimpio/application/sales/dto/` — SaleRequest, SaleResponse, SaleItemRequest, SaleItemResponse
- `src/main/java/com/mundolimpio/application/sales/mapper/SaleMapper.java` — Entity ↔ DTO
- `src/main/resources/db/migration/` — NO NEW MIGRATION NEEDED (tables exist!)
- `src/test/java/com/mundolimpio/application/sales/` — Integration tests (SaleControllerIT)

### Approaches
1. **Complete Standard Approach** — Follow exact pattern of Product/BulkProduct modules
   - Pros: Consistent with project architecture, well-documented, proper separation of concerns, full OpenAPI docs
   - Cons: More code to write, takes longer
   - Effort: Medium

2. **Minimal Quick Approach** — Only essential features, less boilerplate
   - Pros: Fast delivery, less code
   - Cons: Doesn't follow project patterns, harder to maintain, inconsistent
   - Effort: Low

3. **Hybrid with Shared FIFO** — Reuse ProductionBatchService logic
   - Pros: Less duplicated code, FIFO logic already has foundation
   - Cons: Coupling between modules, harder to test in isolation
   - Effort: Medium-High

### Recommendation
**Use Approach 1 (Complete Standard)** — This project has a clear, consistent architecture (Layered Architecture per module). Following the established pattern ensures:
- Consistency across all modules
- Easier onboarding for new developers
- Proper testing (unit + integration)
- Full OpenAPI documentation

The FIFO logic should be implemented in `SaleService`, using `ProductionBatchRepository.findAllWithStockForFifo()` (already exists in ProductionBatchService) to get batches ordered by productionDate ascending.

### Risks
- **FIFO Race Conditions**: Multiple simultaneous sales could try to deduct from the same batch. Mitigation: Use `@Version` (Optimistic Locking) on ProductionBatch (already present!)
- **Stock Insufficient**: Sale requests more than available stock. Mitigation: Check total available stock before processing, return 400 with clear error
- **Transactional Integrity**: Sale + SaleItems + Batch updates must be atomic. Mitigation: Use `@Transactional` on service method
- **Security**: Need to decide if sales are ADMIN-only or also OPERATOR. Current modules suggest ADMIN for write operations.

### Ready for Proposal
Yes — Ready to proceed with `sdd-propose` for the **Sales Module** feature. The proposal should include:
- Intent: Implement sales module with FIFO stock deduction
- Scope: New `sales/` module following standard pattern
- Approach: Complete standard approach with FIFO logic in SaleService
- Affected: New module, no changes to existing modules (but reads from ProductionBatch)
- Security: TBD (suggest ADMIN only for MVP)
