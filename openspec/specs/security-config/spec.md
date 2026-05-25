# Security Configuration Specification

## Purpose

Define the security filter chain configuration for the MundoLimpio backend, ensuring that product catalog endpoints are publicly accessible while maintaining authentication for all other routes.

## Requirements

### Requirement: SEC-CONFIG-001 — Public Product Read Access

All GET endpoints under `/api/v1/products` MUST be accessible without any authentication header.

#### Scenario: List active products without auth
- **GIVEN** no Authorization header
- **WHEN** GET `/api/v1/products`
- **THEN** status MUST be 200 OK

#### Scenario: Get product by ID without auth
- **GIVEN** no Authorization header AND a product with ID 1 exists
- **WHEN** GET `/api/v1/products/1`
- **THEN** status MUST be 200 OK

#### Scenario: Get product by SKU without auth
- **GIVEN** no Authorization header AND a product with SKU "TEST-001" exists
- **WHEN** GET `/api/v1/products/sku/TEST-001`
- **THEN** status MUST be 200 OK

#### Scenario: List all products (inactive included) without auth
- **GIVEN** no Authorization header
- **WHEN** GET `/api/v1/products/all`
- **THEN** status MUST be 200 OK

### Requirement: SEC-CONFIG-002 — Public Write Access to Products

All POST, PUT, DELETE, and PATCH endpoints under `/api/v1/products` MUST be publicly accessible while ProductController has no `@PreAuthorize` annotations.

#### Scenario: Create product without auth
- **GIVEN** no Authorization header
- **WHEN** POST `/api/v1/products` with valid JSON body
- **THEN** status MUST be 201 Created (or 400 if validation fails) — MUST NOT be 401

#### Scenario: Update product without auth
- **GIVEN** no Authorization header AND product with ID 1 exists
- **WHEN** PUT `/api/v1/products/1` with valid JSON body
- **THEN** status MUST be 200 OK — MUST NOT be 401

#### Scenario: Soft-delete product without auth
- **GIVEN** no Authorization header AND product with ID 1 exists
- **WHEN** DELETE `/api/v1/products/1`
- **THEN** status MUST be 204 No Content — MUST NOT be 401

#### Scenario: Reactivate product without auth
- **GIVEN** no Authorization header AND inactive product with ID 1 exists
- **WHEN** PATCH `/api/v1/products/1/reactivate`
- **THEN** status MUST be 204 No Content — MUST NOT be 401

### Requirement: SEC-CONFIG-003 — Path Pattern Correctness

The SecurityConfig `requestMatchers` pattern for product routes MUST use `/api/v1/products/**` (plural) to match ProductController's `@RequestMapping("/api/v1/products")`.

#### Scenario: Plural pattern matches all product sub-paths
- **GIVEN** SecurityConfig has `.requestMatchers("/api/v1/products/**").permitAll()`
- **WHEN** any HTTP request targets any path under `/api/v1/products/`
- **THEN** the SecurityFilterChain MUST NOT require authentication

### Requirement: SEC-CONFIG-004 — Authenticated Routes Still Protected

All non-product endpoints MUST require authentication via `anyRequest().authenticated()`.

#### Scenario: Protected sales endpoint returns 401 without auth
- **GIVEN** no Authorization header
- **WHEN** GET `/api/v1/sales`
- **THEN** status MUST be 401 Unauthorized

#### Scenario: Authenticated user can access protected endpoints
- **GIVEN** a valid JWT token
- **WHEN** GET `/api/v1/sales`
- **THEN** status MUST NOT be 401 (delegated to method-level security)

### Requirement: SEC-CONFIG-005 — No Regression on Existing Security

The security configuration changes MUST NOT break existing authentication or authorization behavior for any other endpoint group.

#### Scenario: Existing actuator endpoints remain accessible
- **GIVEN** no Authorization header
- **WHEN** GET `/actuator/health`
- **THEN** status MUST be 200 OK (actuator has its own permitAll pattern)

## Non-functional Requirements

- The fix MUST NOT alter authentication behavior for non-product endpoints (auth, sales, actuator, etc.)
- All existing security integration tests MUST continue passing unchanged
- The change is limited to a single path string — zero behavioral logic changes elsewhere

---

*Synced from delta spec `sdd/fix-security-config-path/spec` — change archived 2026-05-19*
