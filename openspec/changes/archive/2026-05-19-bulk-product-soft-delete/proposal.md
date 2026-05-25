# Proposal: Convert BulkProduct to soft delete

## Intent

BulkProduct DELETE currently hard-deletes via `repository.deleteById()`. This breaks FK integrity: `production_batches` has `ON DELETE RESTRICT` (throws constraint violation) and `purchase_items` has `ON DELETE SET NULL` (silently loses references). No audit trail. Fix by mirroring the Product module's soft delete pattern: add `active` boolean, DELETE marks inactive, PATCH reactivates.

## Scope

### In Scope
- Add `active BOOLEAN NOT NULL DEFAULT TRUE` column to `bulk_products` (Flyway V6)
- Add `active` field to `BulkProduct` entity (default `true`)
- Add `findByActiveTrue()` to `BulkProductRepository`
- Change `BulkProductService.deleteBulkProduct()` to soft delete (set active=false)
- Add `BulkProductService.reactivateBulkProduct()` method
- Change `BulkProductService.getAllBulkProducts()` to filter by `active = true`
- Add `GET /api/v1/bulk-products/all` endpoint (returns all, admin only)
- Add `PATCH /api/v1/bulk-products/{id}/reactivate` endpoint
- Add `active` field to `BulkProductResponse` DTO
- Update `BulkProductMapper.toResponse()` to include `active`
- Update existing `BulkProductControllerIT.shouldDeleteBulkProduct()` test (currently asserts `existsById == false`)
- Add new tests: soft delete verification, reactivate, GET filters active only, GET /all returns all

### Out of Scope
- Hard delete endpoint (Product has `deleteProductPermanent` but not exposed via controller)
- Adding `deletedAt` timestamp (Product module doesn't use it; keep consistent)
- Bulk soft delete (multi-ID delete) — no requirement
- Test for BulkProductService in isolation (service tested via controller IT is sufficient)

## Capabilities

### New Capabilities
- `bulkproduct-soft-delete`: Soft delete + reactivate for bulk products, with active/inactive query filtering

### Modified Capabilities
None — pure implementation change, no spec-level requirement changes.

## Approach

Mirror Product module exactly:

1. **Entity**: Add `private Boolean active = true;` + getter/setter to `BulkProduct`
2. **Migration V6**: `ALTER TABLE bulk_products ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;`
3. **Repository**: Add `List<BulkProduct> findByActiveTrue();`
4. **Service**: `deleteBulkProduct` → `findById` → `setActive(false)` → `save` (not `deleteById`). Add `reactivateBulkProduct`. Change `getAllBulkProducts` → `findByActiveTrue`. Add `getAllBulkProductsAdmin` → `findAll`.
5. **Controller**: Keep `DELETE /{id}` as soft delete. Add `GET /all`. Add `PATCH /{id}/reactivate`. Update doc comments.
6. **DTO**: Add `Boolean active` to `BulkProductResponse` record.
7. **Mapper**: Include `active` in `toResponse()`.
8. **Tests**: Update existing IT to verify active=false instead of not-exists. Add 4 new IT methods and 7 unit tests.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `domain/BulkProduct.java` | Modified | Add `active` field |
| `resources/db/migration/V6__Add_Active_To_Bulk_Products.sql` | **New** | Add active column |
| `repository/BulkProductRepository.java` | Modified | Add `findByActiveTrue()` |
| `service/BulkProductService.java` | Modified | Soft delete + reactivate + filtered queries |
| `controller/BulkProductController.java` | Modified | Add /all + reactivate endpoints |
| `dto/BulkProductResponse.java` | Modified | Add `active` field |
| `mapper/BulkProductMapper.java` | Modified | Map `active` in toResponse |
| `controller/BulkProductControllerIT.java` | Modified | Update delete test + add new tests |
| `service/BulkProductServiceTest.java` | **New** | 7 unit tests with Mockito |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Existing FK constraints violated during migration | Low | Additive migration (new column, no FK changes). All existing rows default to active=true |
| Existing test `shouldDeleteBulkProduct` fails with soft delete | **Certain** | Must update assertion from `existsById == false` to query-by-id and verify `active == false` |
| Downstream services querying BulkProduct by `findAll()` get inactive records | Low | All queries now use `findByActiveTrue()`. /all endpoint exists for admin use |
| BulkProductRequest doesn't include active — new products always default to active | None — by design | Matches Product pattern exactly |

## Rollback Plan

1. Revert all Java file changes (entity, service, controller, repository, mapper, dto, test)
2. Drop Flyway V6 migration: `DELETE FROM flyway_schema_history WHERE version = '6';` then `ALTER TABLE bulk_products DROP COLUMN active;`
3. Or: just keep the column (additive change is backward compatible)

## Dependencies

- Product module soft delete pattern — exact template to follow
- AbstractIntegrationTest (Testcontainers) for IT — already set up

## Success Criteria

- [ ] `DELETE /api/v1/bulk-products/{id}` marks `active = false` (row persists in DB)
- [ ] `PATCH /api/v1/bulk-products/{id}/reactivate` sets `active = true`
- [ ] `GET /api/v1/bulk-products` returns only active records
- [ ] `GET /api/v1/bulk-products/all` returns all records (including inactive)
- [ ] Attempting to delete a non-existent ID returns 404
- [ ] Attempting to reactivate a non-existent ID returns 404
- [ ] No FK constraint violations when deleting bulk products with associated batches
- [ ] All existing tests pass with updated assertions

---

*Archived from Engram artifact `sdd/bulk-product-soft-delete/proposal` (ID: #317)*
