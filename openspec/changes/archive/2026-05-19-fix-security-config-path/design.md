# Design: Fix SecurityConfig product path mismatch

## Technical Approach

Single-line typo fix in `SecurityConfig.java`. The `requestMatchers` pattern uses singular `/api/v1/product/**` but `ProductController` maps to `/api/v1/products` (plural). Spring Security's `AntPathMatcher` never matches the controller's real paths, so all 8 product endpoints silently require authentication. The fix replaces `product` → `products` on one line. Zero behavioral logic changes. Add a security integration test to prevent regression.

## Architecture Decisions

### Decision: Path string replacement only

| Option | Tradeoff | Decision |
|--------|----------|----------|
| Change SecurityConfig pattern to plural | Minimal, zero risk | **Chosen** |
| Change ProductController `@RequestMapping` to singular | Breaks API contract; all clients and 57 test references use `/api/v1/products` | Rejected |
| Add both patterns (singular OR plural) | Leaves dead pattern, masks the problem | Rejected |

**Rationale**: The controller path is the source of truth (57 test references across 3 test classes use `/api/v1/products`). SecurityConfig must match it. One-line fix with zero ambiguity.

### Decision: Standalone test with own PostgreSQLContainer

| Option | Tradeoff | Decision |
|--------|----------|----------|
| Extend `AbstractIntegrationTest` | Simpler setup, inherits mocks | Rejected — adds unnecessary coupling to @MockBean for ITesseract/S3Client |
| Standalone with own `@Container` | Same pattern as `SecurityConfigActuatorTest`, self-contained, no dependency on unrelated mocks | **Chosen** |

**Rationale**: The existing `SecurityConfigActuatorTest` follows this pattern. It isolates the security test from application-layer mocks. The test only needs MockMvc + PostgreSQL (for Flyway migrations).

## Data Flow

```
Browser / Flutter App
  │
  ▼
Tomcat (RANDOM_PORT)
  │
  ▼
SecurityFilterChain
  │
  ├─ matches /api/v1/products/** → permitAll → no auth required
  │  └─ DispatcherServlet → ProductController → Service → Repository → PostgreSQL
  │
  └─ no match → anyRequest().authenticated()
     └─ JwtAuthenticationFilter → no token → 401 UNAUTHORIZED
```

No data flow changes. Same filter chain, same controller, same service layer.

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `src/main/java/.../SecurityConfig.java` | Modify | Line 75: `/api/v1/product/**` → `/api/v1/products/**` |
| `src/test/java/.../SecurityConfigProductAccessTest.java` | Create | Security integration test verifying public product access + regression guard |

### SecurityConfig.java — Before/After

```java
// BEFORE (line 75)
.requestMatchers("/api/v1/product/**").permitAll()

// AFTER
.requestMatchers("/api/v1/products/**").permitAll()
```

## Testing Strategy

| Layer | What to Test | Approach |
|-------|-------------|----------|
| Integration | Product endpoints return 200/201/204 without auth | `MockMvc` against full Spring context (`@SpringBootTest RANDOM_PORT`) |
| Integration | Authenticated user also gets 200 on product endpoints | `@WithMockUser` triangulation |
| Integration | Unauthenticated protected endpoint (sales) returns 401 | Regression guard using same pattern as `CorsSecurityTest` |
| Integration | Preflight OPTIONS to /products returns 200 | Verifies CORS filter precedes auth |

### Test class: `SecurityConfigProductAccessTest.java`

Package: `com.mundolimpio.application.security.config`

Follows the exact pattern of `SecurityConfigActuatorTest`:
- `@SpringBootTest(webEnvironment = RANDOM_PORT)`
- `@AutoConfigureMockMvc`
- `@ActiveProfiles("test")`
- `@Testcontainers`
- Own `@Container static PostgreSQLContainer` with `@DynamicPropertySource`

**Test methods:**
1. `getProducts_WithoutAuth_Returns200`
2. `getProducts_WithAuth_Returns200`
3. `getProductById_WithoutAuth_Returns404or200`
4. `optionsProducts_WithoutAuth_Returns200`
5. `getSales_WithoutAuth_Returns401`

## Migration / Rollout

No migration required. No schema changes, no data changes, no configuration changes.

**Rollback**: Revert the single line in SecurityConfig.java. Delete the test file.

## Dependencies

None. No new libraries, no new configuration, no API changes.

---

*Archived from Engram artifact `sdd/fix-security-config-path/design` (ID: #310)*
