# Tasks: Product Controller Tests

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~350–400 (all additions, zero deletions) |
| 400-line budget risk | Medium |
| Chained PRs recommended | No |
| Suggested split | Single PR |
| Delivery strategy | ask-on-risk |
| Chain strategy | stacked-to-develop |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: stacked-to-develop
400-line budget risk: Medium

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | ProductMapperTest (4 tests, ~80 lines) | Single PR, commit 1 | base=develop; independent file, no production changes |
| 2 | ProductServiceTest (17 tests, ~320 lines) | Single PR, commit 2 | base=develop; independent file, no production changes |

## Phase 1: ProductMapperTest (Testcontainers-based component test)

- [x] 1.1 Read `SaleMapperTest.java` as reference pattern for extending `AbstractIntegrationTest` with `@Autowired`
- [x] 1.2 Create `src/test/java/com/mundolimpio/application/product/mapper/ProductMapperTest.java` with 4 test methods:
  - `toEntity_ShouldMapAllFields` — ProductRequest → Product field mapping + active=true
  - `toEntity_ShouldSetActiveToTrueByDefault` — verifies active defaults to true
  - `toResponse_ShouldMapAllFieldsFromEntity` — Product → ProductResponse field mapping
  - `toResponse_ShouldPreserveActiveField` — verifies active field preserved
- [x] 1.3 Run `mvn test -Dtest=ProductMapperTest` — verified 4/4 pass
- [x] 1.4 Commit: `test(product): add ProductMapper unit tests` (2182598)

## Phase 2: ProductServiceTest (Mockito unit tests)

- [x] 2.1 Read `InventoryServiceTest.java` as reference pattern for `@ExtendWith(MockitoExtension.class)`, `@Mock`, `@InjectMocks`
- [x] 2.2 Create `src/test/java/com/mundolimpio/application/product/service/ProductServiceTest.java` with 17 test methods:
  - **Create (3):** `createProduct_Success_ReturnsResponse`, `createProduct_DuplicateSku_ThrowsAlreadyExists`, `createProduct_ValidationError_Throws`
  - **Get by ID (2):** `getProductById_ExistingId_ReturnsResponse`, `getProductById_NonExistentId_ThrowsNotFound`
  - **Get by SKU (2):** `getProductBySku_ExistingSku_ReturnsResponse`, `getProductBySku_NonExistentSku_ThrowsNotFound`
  - **Get all active (1):** `getAllActiveProducts_ReturnsOnlyActiveProducts`
  - **Get all (1):** `getAllProducts_ReturnsAllProductsIncludingInactive`
  - **Update (3):** `updateProduct_Success_ReturnsUpdatedResponse`, `updateProduct_NonExistentId_ThrowsNotFound`, `updateProduct_SkuConflict_ThrowsAlreadyExists`
  - **Soft delete (2):** `deleteProductSoftDelete_Success_MarksInactive`, `deleteProductSoftDelete_NonExistentId_ThrowsNotFound`
  - **Reactivate (3):** `reactivateProduct_Success_MarksActive`, `reactivateProduct_AlreadyActive_Idempotent`, `reactivateProduct_NonExistentId_ThrowsNotFound`
- [x] 2.3 Run `mvn test -Dtest=ProductServiceTest` — verified 17/17 pass
- [x] 2.4 Commit: `test(product): add ProductService unit tests` (d3399ae)

## Phase 3: Full Regression Verification

- [x] 3.1 Run `mvn clean test` — 176 tests, 0 failures, 0 errors, 0 skipped
- [x] 3.2 All green — no regressions
- [ ] 3.3 Push branch and open single PR with 2 commits

## Implementation Order

Phase 1 first (small, fast, builds confidence with Testcontainers context load). Then Phase 2 (larger, pure Mockito, no Docker). Phase 3 last to confirm no regressions.

## Commit Plan

```text
1. test(product): add ProductMapper unit tests  (2182598)
2. test(product): add ProductService unit tests (d3399ae)
```
