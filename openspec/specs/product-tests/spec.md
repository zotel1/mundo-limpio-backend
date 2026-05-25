# Spec: Product Controller Tests

## Overview

Delta spec for Product Controller Tests — unit and integration test coverage for the Product module (service, mapper, controller). No production code changes. Strict TDD.

## Requirements

### PROD-TEST-001 — Product Creation

The test suite MUST verify product creation flows including happy path, duplicate SKU rejection, validation, and HTTP boundary conditions.

Scenarios:
| # | Given | When | Then |
|---|-------|------|------|
| 1 | A unique SKU is provided | `createProduct` is called | Product is saved and a `ProductResponse` is returned with correct fields |
| 2 | An existing SKU is provided | `createProduct` is called | `ProductAlreadyExistsException` is thrown and `save()` is never called |
| 3 | A validation error occurs during save | `createProduct` is called | RuntimeException propagates to the caller |
| 4 | An invalid SKU pattern is sent via HTTP | POST /api/products is called with invalid SKU | HTTP 400 Bad Request is returned |
| 5 | A negative price is sent via HTTP | POST /api/products is called with negative price | HTTP 400 Bad Request is returned |

### PROD-TEST-002 — Product Retrieval

The test suite MUST verify product retrieval flows: by ID, by SKU, all active, and all (including inactive).

Scenarios:
| # | Given | When | Then |
|---|-------|------|------|
| 1 | An existing product ID | `getProductById` is called | ProductResponse is returned with matching ID |
| 2 | A non-existent product ID | `getProductById` is called | `ProductNotFoundException` is thrown |
| 3 | An existing SKU | `getProductBySku` is called | ProductResponse is returned with matching SKU |
| 4 | A non-existent SKU | `getProductBySku` is called | `ProductNotFoundException` is thrown |
| 5 | Active products exist | `getAllActiveProducts` is called | Only active products are returned |
| 6 | Products exist (both active and inactive) | `getAllProducts` is called | All products including inactive are returned |

### PROD-TEST-003 — Product Update

The test suite MUST verify product update flows including success, not-found, and SKU conflict.

Scenarios:
| # | Given | When | Then |
|---|-------|------|------|
| 1 | An existing product with a new unique SKU | `updateProduct` is called | Fields are updated, product is saved, updated `ProductResponse` is returned |
| 2 | A non-existent product ID | `updateProduct` is called | `ProductNotFoundException` is thrown |
| 3 | A new SKU that conflicts with another product | `updateProduct` is called | `ProductAlreadyExistsException` is thrown and `save()` is never called |

### PROD-TEST-004 — Soft Delete + Reactivate

The test suite MUST verify soft delete and reactivate flows including success, not-found, and idempotent reactivation.

Scenarios:
| # | Given | When | Then |
|---|-------|------|------|
| 1 | An existing active product | `deleteProductSoftDelete` is called | Product is marked `active=false` and saved |
| 2 | A non-existent product ID | `deleteProductSoftDelete` is called | `ProductNotFoundException` is thrown |
| 3 | An existing inactive product | `reactivateProduct` is called | Product is marked `active=true` and saved |
| 4 | A non-existent product ID | `reactivateProduct` is called | `ProductNotFoundException` is thrown |
| 5 | An already active product | `reactivateProduct` is called | Operation is idempotent — no error, product remains active |

### PROD-TEST-005 — Mapper

The test suite MUST verify ProductMapper entity↔DTO conversion including all fields and edge cases.

Scenarios:
| # | Given | When | Then |
|---|-------|------|------|
| 1 | A `ProductRequest` with all fields populated | `toEntity` is called | All fields are mapped correctly, `active=true` is set |
| 2 | A `ProductRequest` with null `minPrice` | `toEntity` is called | `minPrice` is null in entity, no NPE thrown |
| 3 | A `Product` entity with all fields populated | `toResponse` is called | All fields are mapped correctly, `active` flag is preserved |
| 4 | A `Product` entity with `active=true` | `toResponse` is called | `active` field is preserved as `true` in response |

### PROD-TEST-006 — No Regression

The full test suite MUST pass without regressions after adding new test files.

Scenarios:
| # | Given | When | Then |
|---|-------|------|------|
| 1 | All existing (155) and new (21) test files | `mvn clean test` is executed | All 176 tests pass: 0 failures, 0 errors, 0 skipped |
