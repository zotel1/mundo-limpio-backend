## JwtAuthenticationFilter Specification

### Purpose

Defines how `JwtAuthenticationFilter` handles requests with expired, valid, or absent JWT tokens. Documents the fix for `ExpiredJwtException` from `parseSignedClaims()` propagating uncaught outside the try-catch block on line 51 of the original code.

### Requirements

#### JWT-FILTER-001: Expired token on permitAll endpoint — MUST NOT return 401

When a request carries an expired Bearer token targeting a `.permitAll()` endpoint, the filter MUST silently swallow the exception, skip SecurityContext population, and continue the filter chain. The controller MUST process the request normally.

**Scenario: Expired token on POST /api/v1/auth/register**
- GIVEN an expired JWT Bearer token (signed with the same `application.security.jwt.secret-key` but with a past `exp` claim)
- WHEN POST `/api/v1/auth/register` with header `Authorization: Bearer <expired-token>`
- THEN the HTTP response status MUST NOT be 401
- AND the response MUST originate from AuthController (201 Created on valid registration body, or 409 Conflict)

**Scenario: Expired token on GET /api/v1/products**
- GIVEN an expired JWT Bearer token
- WHEN GET `/api/v1/products` with `Authorization: Bearer <expired-token>`
- THEN the HTTP response status MUST NOT be 401
- AND the response MUST be 200 OK

#### JWT-FILTER-002: Expired token on protected endpoint — MUST return 401

When a request carries an expired Bearer token targeting a non-`.permitAll()` endpoint, the filter MUST NOT set SecurityContext authentication. The `AuthenticationEntryPoint` MUST return 401.

**Scenario: Expired token on protected endpoint**
- GIVEN an expired JWT Bearer token
- WHEN GET `/api/v1/sales` with `Authorization: Bearer <expired-token>`
- THEN the HTTP response status MUST be 401 Unauthorized

#### JWT-FILTER-003: Valid token on protected endpoint — MUST authenticate

When a request carries a valid non-expired Bearer token targeting a protected endpoint, the filter MUST extract the username, load UserDetails, validate the token, and populate SecurityContext. The endpoint MUST process the request normally.

**Scenario: Valid token on protected endpoint**
- GIVEN a valid non-expired JWT Bearer token for a registered user
- WHEN GET `/api/v1/sales` with `Authorization: Bearer <valid-token>`
- THEN the HTTP response status MUST NOT be 401
- AND SecurityContextHolder MUST contain the authenticated user

#### JWT-FILTER-004: No token on permitAll endpoint — MUST process normally

When no `Authorization` header is present on a `.permitAll()` endpoint, the filter MUST skip processing entirely, call `filterChain.doFilter()` directly, and let the controller handle the request.

**Scenario: No Authorization header on GET /api/v1/products**
- GIVEN a request without an `Authorization` header
- WHEN GET `/api/v1/products`
- THEN the HTTP response status MUST be 200 OK

### Test Infrastructure

Tests MUST use the same patterns as existing integration tests:
- `@SpringBootTest(webEnvironment = RANDOM_PORT)` with `@AutoConfigureMockMvc` and `@ActiveProfiles("test")`
- Extend `AbstractIntegrationTest` for PostgreSQL via Testcontainers, or use a dedicated `@Container`
- Generate expired tokens via `Jwts.builder().subject("test").issuedAt(past).expiration(past).signWith(key).compact()` using the same HMAC key from `application.security.jwt.secret-key`
- No `@WithMockUser` needed — tests send real Bearer tokens through the full filter chain

### Regression Guards

- `SecurityConfigProductAccessTest` tests must still pass (permitAll endpoints without auth)
- Existing auth integration tests must still pass (valid token flow)
