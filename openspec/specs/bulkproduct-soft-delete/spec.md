# Bulk Product Soft Delete Specification

## Purpose

Convert BulkProduct from hard delete to soft delete, mirroring the Product module pattern. DELETE marks `active=false` instead of removing the row, PATCH reactivates, GET defaults to active-only. Preserves FK integrity with `production_batches` (ON DELETE RESTRICT) and `purchase_items` (ON DELETE SET NULL).

## Requirements

### Requirement: BULK-SOFT-001 — Soft Delete

DELETE `/api/v1/bulk-products/{id}` MUST set `active = false` instead of calling `repository.deleteById()`.

#### Scenario: Soft delete marks inactive
- GIVEN a bulk product with ID 1 exists
- WHEN DELETE /api/v1/bulk-products/1
- THEN status MUST be 204 No Content
- AND the row MUST still exist in `bulk_products` with `active = false`

#### Scenario: No FK violation with associated batches
- GIVEN a bulk product with ID 1 AND production_batches reference it (FK fk_batch_bulk_product, ON DELETE RESTRICT)
- WHEN DELETE /api/v1/bulk-products/1
- THEN status MUST be 204 No Content (soft delete does not remove the row, FK constraint is satisfied)

#### Scenario: Non-existent ID returns 404
- GIVEN no bulk product with ID 999
- WHEN DELETE /api/v1/bulk-products/999
- THEN status MUST be 404 Not Found

### Requirement: BULK-SOFT-002 — Reactivate

PATCH `/api/v1/bulk-products/{id}/reactivate` MUST set `active = true`.

#### Scenario: Reactivate inactive product
- GIVEN a bulk product with ID 1 AND `active = false`
- WHEN PATCH /api/v1/bulk-products/1/reactivate
- THEN status MUST be 204 No Content
- AND GET /api/v1/bulk-products MUST include this product

#### Scenario: Reactivate already active product (idempotent)
- GIVEN a bulk product with ID 1 AND `active = true`
- WHEN PATCH /api/v1/bulk-products/1/reactivate
- THEN status MUST be 204 No Content
- AND the row remains `active = true`

#### Scenario: Reactivate non-existent ID returns 404
- GIVEN no bulk product with ID 999
- WHEN PATCH /api/v1/bulk-products/999/reactivate
- THEN status MUST be 404 Not Found

### Requirement: BULK-SOFT-003 — Query Filtering

GET `/api/v1/bulk-products` MUST return only active products. GET `/api/v1/bulk-products/all` MUST return all (active and inactive).

#### Scenario: Default GET filters inactive
- GIVEN 3 bulk products: 2 active, 1 inactive
- WHEN GET /api/v1/bulk-products
- THEN response MUST contain exactly 2 products, all with `active = true`

#### Scenario: GET /all returns everything
- GIVEN 3 bulk products: 2 active, 1 inactive
- WHEN GET /api/v1/bulk-products/all
- THEN response MUST contain exactly 3 products

#### Scenario: No inactive — both endpoints return same count
- GIVEN all bulk products are active
- WHEN GET /api/v1/bulk-products AND GET /api/v1/bulk-products/all
- THEN both MUST return the same count

### Requirement: BULK-SOFT-004 — Data Integrity

Soft-deleted bulk products MUST NOT affect existing references in dependent tables.

#### Scenario: Production batch retains FK reference
- GIVEN a production_batches row references bulk_product_id = 1
- WHEN the referenced bulk product is soft-deleted
- THEN the batch row MUST still be queryable with the same bulk_product_id
- AND FK constraint fk_batch_bulk_product is satisfied (row still exists)

#### Scenario: Purchase items retain FK reference
- GIVEN a purchase_items row references bulk_product_id = 1 (ON DELETE SET NULL)
- WHEN the referenced bulk product is soft-deleted
- THEN the purchase_item MUST still reference bulk_product_id = 1 (not nulled, because the row persists)

### Requirement: BULK-SOFT-005 — Migration Backward Compatibility

Flyway V6 MUST add `active BOOLEAN NOT NULL DEFAULT TRUE` to `bulk_products` without data loss.

#### Scenario: Additive column with default
- GIVEN `bulk_products` table exists with data from V1/V3
- WHEN V6 migration runs
- THEN `active BOOLEAN NOT NULL DEFAULT TRUE` MUST be added
- AND all existing rows MUST have `active = true`

#### Scenario: Rollback-safe (additive)
- GIVEN V6 migration has run
- WHEN the change is rolled back
- THEN the `active` column can remain — additive change is backward compatible
- AND legacy Java code that does not reference `active` continues to work

---

*Synced from delta spec `sdd/bulk-product-soft-delete/spec` — change archived 2026-05-19*
