# Design: Convert BulkProduct to Soft Delete

## 1. Current State Analysis

**How hard delete works now:**

```
Client ──DELETE──▶ BulkProductController.deleteBulkProduct()
                       └──▶ BulkProductService.deleteBulkProduct()
                                └──▶ repository.existsById(id)  // check
                                └──▶ repository.deleteById(id)   // DELETE FROM
```

**What breaks:**
- `production_batches` FK (`fk_batch_bulk_product`, `ON DELETE RESTRICT`) — deletes throw constraint violation if batches reference the bulk product (V4 migration)
- `purchase_items` FK (`fk_item_bulk_product`, `ON DELETE SET NULL`) — deletes silently lose the reference (V5 migration)
- No audit trail — once deleted, the row is gone
- Integration test `BulkProductControllerIT.shouldDeleteBulkProduct` asserts `assertFalse(existsById)` — will fail after soft delete
- No `@Transactional` on service (ProductService has it)

## 2. Proposed Architecture

Mirror `Product` module soft delete exactly. Every decision mirrors Product behavior:

| Decision | Choice | Product Precedent |
|----------|--------|-------------------|
| Delete mechanism | `entity.setActive(false)` + `repository.save()` | `deleteProductSoftDelete()` |
| Reactivate mechanism | `entity.setActive(true)` + `repository.save()` | `reactivateProduct()` |
| Active filtering | `findByActiveTrue()` for default GET | `getAllActiveProducts()` |
| All records access | `findAll()` for `/all` endpoint | `getAllProducts()` |
| DTO field position | `active` as LAST field in record | `ProductResponse(..., Boolean active)` |
| Entity field | `private Boolean active = true;` with `@Column` | `Product.active` (default true) |
| Mapper new entity | Explicit `entity.setActive(true)` in `toEntity` | `ProductMapper.toEntity()` |
| Transaction boundary | Add `@Transactional` to service | `ProductService` has it |
| Exception handling | Reuse existing `BulkProductNotFoundException` (already handled via `@ControllerAdvice`) | `ProductNotFoundException` |

## 3. Data Flow Diagrams

### DELETE (before → after)

```
BEFORE (hard delete):
  DELETE /{id} → existsById? → yes → DELETE FROM bulk_products → 204
                                          ↓
                         FK RESTRICT → 500 (if batches exist)

AFTER (soft delete):
  DELETE /{id} → findById? → yes → setActive(false) → save → 204
                       ↓ no → 404                               ↓
                                                     Row persists, FKs satisfied
```

### GET (filtered)

```
  GET /api/v1/bulk-products      → findByActiveTrue() → stream → map → list → 200
  GET /api/v1/bulk-products/all  → findAll()          → stream → map → list → 200
```

### PATCH reactivate (NEW)

```
  PATCH /{id}/reactivate → findById? → yes → setActive(true) → save → 204
                                ↓ no → 404
```

## 4. Migration Strategy

**V6__Add_Active_To_Bulk_Products.sql** (new file):

```sql
-- WHAT: Agrega columna active para soft delete de materias primas.
-- WHY: FK constraints (ON DELETE RESTRICT en production_batches,
--      ON DELETE SET NULL en purchase_items) requieren que la fila persista.
-- DIFFERENCES: V3 agregó conversion_ratio; V6 agrega active.
ALTER TABLE bulk_products ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
```

- **Additive only**: no data loss, no FK changes
- **Default TRUE**: all existing rows become active automatically
- **Rollback**: `DELETE FROM flyway_schema_history WHERE version = '6';` then `ALTER TABLE bulk_products DROP COLUMN active;` — or just leave it (backward compatible)

## 5. Test Strategy

### Unit Tests — `BulkProductServiceTest.java` (NEW)

| Test | Purpose |
|------|---------|
| `shouldSoftDeleteBulkProduct` | Verify `active=false` after delete, row still exists |
| `shouldReactivateBulkProduct` | Verify `active=true` after reactivate |
| `shouldReactivateAlreadyActiveProduct` | Idempotent: no-op on active=true |
| `shouldThrowNotFoundOnDeleteNonexistent` | 404 on delete of missing ID |
| `shouldThrowNotFoundOnReactivateNonexistent` | 404 on reactivate of missing ID |
| `shouldFilterInactiveFromGetAll` | `getAllBulkProducts()` returns only active |
| `shouldReturnAllIncludingInactiveFromAdmin` | `getAllBulkProductsAdmin()` returns everything |

Uses `@ExtendWith(MockitoExtension.class)`, mocks `BulkProductRepository` and `BulkProductMapper`.

### Integration Tests — `BulkProductControllerIT.java` (MODIFY)

| Test | Change |
|------|--------|
| `shouldDeleteBulkProduct` | Replace `assertFalse(existsById(id))` with query-by-id and assert `active==false` |
| `shouldGetAllBulkProducts` | Create 1 inactive + 2 active; verify GET returns only active |
| `shouldGetAllBulkProductsIncludingInactive` (NEW) | Verify GET `/all` returns all 3 |
| `shouldReactivateBulkProduct` (NEW) | Soft-delete then PATCH reactivate, verify active=true |
| `shouldReturn404OnDeleteNonexistent` (NEW) | DELETE /999 → 404 |
| `shouldReturn404OnReactivateNonexistent` (NEW) | PATCH /999/reactivate → 404 |

## 6. File-by-File Change Specification

### 1. `BulkProduct.java` — MODIFY
Add `@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE") private Boolean active = true;` with getter/setter.

### 2. `V6__Add_Active_To_Bulk_Products.sql` — NEW
Additive migration with DEFAULT TRUE.

### 3. `BulkProductRepository.java` — MODIFY
Add `List<BulkProduct> findByActiveTrue();`

### 4. `BulkProductService.java` — MODIFY
- Add `@Transactional` at class level
- Replace `deleteBulkProduct`: `findById` → `setActive(false)` → `save`
- Add `reactivateBulkProduct(Long id)`: `findById` → `setActive(true)` → `save`
- Change `getAllBulkProducts()`: use `repository.findByActiveTrue()`
- Add `getAllBulkProductsAdmin()`: uses `repository.findAll()`

### 5. `BulkProductController.java` — MODIFY
- DELETE endpoint doc: update to "Performs a soft delete"
- Add `PATCH /{id}/reactivate` endpoint
- Add `GET /all` endpoint with `@PreAuthorize("hasRole('ADMIN')")`
- Update Javadoc header

### 6. `BulkProductResponse.java` — MODIFY
Add `Boolean active` as last record field.

### 7. `BulkProductMapper.java` — MODIFY
- `toEntity()`: add `entity.setActive(true);`
- `toResponse()`: add `entity.getActive()` as 6th parameter

### 8. `BulkProductControllerIT.java` — MODIFY
Update `shouldDeleteBulkProduct` assertion + add 4 new tests.

## 7. Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|------------|
| Existing test breaks (existsById assertion) | **High** — certain | Updated in same PR to query-by-id + verify `active=false` |
| Missing `@Transactional` causes lazy-load issues | Low | Added `@Transactional` matching ProductService |
| Query returning all records (no filter) in downstream | Low | Default GET now filters `findByActiveTrue()`; `/all` for admin only |
| V6 migration conflicts with existing tables | None | Additive column with default, no FK changes |

---

*Archived from Engram artifact `sdd/bulk-product-soft-delete/design` (ID: #319)*
