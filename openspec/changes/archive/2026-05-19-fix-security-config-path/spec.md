# Product Catalog Public Access Specification

## Purpose

Define that all product catalog endpoints under `/api/v1/products` MUST be publicly accessible without authentication, fixing the SecurityConfig path pattern that used singular `/api/v1/product/**` (a no-op pattern that never matched).

## Requirements

### Requirement: PROD-ACCESS-001 — Public Product Read Access

All GET endpoints under `/api/v1/products` MUST be accessible without any authentication header.

#### Scenario: List active products without auth
- GIVEN no Authorization header
- WHEN GET /api/v1/products
- THEN status MUST be 200 OK

#### Scenario: Get product by ID without auth
- GIVEN no Authorization header AND a product with ID 1 exists
- WHEN GET /api/v1/products/1
- THEN status MUST be 200 OK

#### Scenario: Get product by SKU without auth
- GIVEN no Authorization header AND a product with SKU "TEST-001" exists
- WHEN GET /api/v1/products/sku/TEST-001
- THEN status MUST be 200 OK

#### Scenario: List all products (inactive included) without auth
- GIVEN no Authorization header
- WHEN GET /api/v1/products/all
- THEN status MUST be 200 OK

### Requirement: PROD-ACCESS-002 — Public Write Access (Current State)

All POST, PUT, DELETE, and PATCH endpoints under `/api/v1/products` MUST be publicly accessible while ProductController has no `@PreAuthorize` annotations.

#### Scenario: Create product without auth
- GIVEN no Authorization header
- WHEN POST /api/v1/products with valid JSON body
- THEN status MUST be 201 Created (or 400 if validation fails) — MUST NOT be 401

#### Scenario: Update product without auth
- GIVEN no Authorization header AND product with ID 1 exists
- WHEN PUT /api/v1/products/1 with valid JSON body
- THEN status MUST be 200 OK — MUST NOT be 401

#### Scenario: Soft-delete product without auth
- GIVEN no Authorization header AND product with ID 1 exists
- WHEN DELETE /api/v1/products/1
- THEN status MUST be 204 No Content — MUST NOT be 401

#### Scenario: Reactivate product without auth
- GIVEN no Authorization header AND inactive product with ID 1 exists
- WHEN PATCH /api/v1/products/1/reactivate
- THEN status MUST be 204 No Content — MUST NOT be 401

### Requirement: PROD-ACCESS-003 — Path Pattern Correctness

The SecurityConfig `requestMatchers` pattern for product routes MUST use `/api/v1/products/**` (plural) to match ProductController's `@RequestMapping("/api/v1/products")`.

#### Scenario: Plural pattern matches all product sub-paths
- GIVEN SecurityConfig has `.requestMatchers("/api/v1/products/**").permitAll()`
- WHEN any HTTP request targets any path under `/api/v1/products/`
- THEN the SecurityFilterChain MUST NOT require authentication

#### Scenario: Singular path (old typo) returns 404 not 401
- GIVEN no Authorization header
- WHEN GET /api/v1/product (singular, no registered endpoint)
- THEN status MUST be 404 Not Found — MUST NOT be 401 Unauthorized

## Non-functional Requirements

- The fix MUST NOT alter authentication behavior for non-product endpoints (auth, sales, actuator, etc.)
- All existing security integration tests MUST continue passing unchanged
- The change is limited to a single path string — zero behavioral logic changes elsewhere

## Edge Cases

### Preflight OPTIONS to product endpoints
- GIVEN an OPTIONS request to /api/v1/products with allowed Origin and Access-Control-Request-Method headers
- THEN status MUST be 200 OK with CORS headers (handled before authentication in the filter chain)

### Authenticated user accessing product endpoints
- GIVEN a valid JWT token in the Authorization header
- WHEN GET /api/v1/products
- THEN status MUST be 200 OK (same as unauthenticated — permitAll applies to all)

---

*Archived from Engram artifact `sdd/fix-security-config-path/spec` (ID: #306)*
