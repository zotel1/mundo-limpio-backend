# Tasks: Bulk Product Soft Delete

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~330 (additions: ~325, deletions: ~5) |
| 400-line budget risk | Medium |
| Chained PRs recommended | No |
| Suggested split | Single PR |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |
| **Actual changed lines** | ~424 (net, incl. Javadoc + imports) |

## Phase 1: Data Layer

- [x] 1.1 Create `resources/db/migration/V6__Add_Active_To_Bulk_Products.sql` — ALTER TABLE ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE
- [x] 1.2 `BulkProduct.java` — add `active` field (+`@Column`), getter/setter
- [x] 1.3 `BulkProductRepository.java` — add `List<BulkProduct> findByActiveTrue()`

## Phase 2: DTO + Mapper

- [x] 2.1 `BulkProductResponse.java` — add `Boolean active` as last record field
- [x] 2.2 `BulkProductMapper.java` — `toEntity()`: add `entity.setActive(true)`; `toResponse()`: pass `entity.getActive()`

## Phase 3: Service Layer

- [x] 3.1 `BulkProductService.java` — add `@Transactional` at class level, add SLF4J Logger
- [x] 3.2 Replace `deleteBulkProduct`: use `findById` + `setActive(false)` + `save` (remove `existsById` + `deleteById`)
- [x] 3.3 Add `reactivateBulkProduct(Long id)`: `findById` + `setActive(true)` + `save`
- [x] 3.4 Change `getAllBulkProducts()` to use `repository.findByActiveTrue()`
- [x] 3.5 Add `getAllBulkProductsAdmin()` using `repository.findAll()`

## Phase 4: Controller + Integration Tests

- [x] 4.1 `BulkProductController.java` — update DELETE endpoint doc to "soft delete" semantics
- [x] 4.2 Add `PATCH /{id}/reactivate` endpoint mirroring ProductController
- [x] 4.3 Add `GET /all` endpoint with `@PreAuthorize("hasRole('ADMIN')")`
- [x] 4.4 Update controller Javadoc header to list new endpoints
- [x] 4.5 `BulkProductControllerIT` — update `shouldDeleteBulkProduct`: replace `assertFalse(existsById)` with verify `active==false`
- [x] 4.6 Modify `shouldGetAllBulkProducts`: create 1 inactive + 2 active, verify GET returns only active
- [x] 4.7 Add `shouldGetAllBulkProductsIncludingInactive`: GET /all returns all
- [x] 4.8 Add `shouldReactivateBulkProduct`: soft-delete, PATCH reactivate, verify active=true
- [x] 4.9 Add `shouldReturn404OnDeleteNonexistent`: DELETE /999 → 404
- [x] 4.10 Add `shouldReturn404OnReactivateNonexistent`: PATCH /999/reactivate → 404

## Phase 5: Service Unit Tests + Regression

- [x] 5.1 Create `BulkProductServiceTest.java` with `@ExtendWith(MockitoExtension.class)`
- [x] 5.2 Test: `shouldSoftDeleteBulkProduct` — verify active=false after delete, row persists
- [x] 5.3 Test: `shouldReactivateBulkProduct` — verify active=true after reactivate
- [x] 5.4 Test: `shouldReactivateAlreadyActiveProduct` — idempotent, no error
- [x] 5.5 Test: `shouldThrowNotFoundOnDeleteNonexistent` — 404 on missing ID
- [x] 5.6 Test: `shouldThrowNotFoundOnReactivateNonexistent` — 404 on missing ID
- [x] 5.7 Test: `shouldFilterInactiveFromGetAll` — getAllBulkProducts returns only active
- [x] 5.8 Test: `shouldReturnAllIncludingInactiveFromAdmin` — getAllBulkProductsAdmin returns all
- [x] 5.9 Run full test suite: 155/155 pass, 0 failures, BUILD SUCCESS
- [x] 5.10 Commit: `fix(bulk-product): convert hard delete to soft delete with active flag`

---

*Archived from Engram artifact `sdd/bulk-product-soft-delete/tasks` (ID: #320)*
