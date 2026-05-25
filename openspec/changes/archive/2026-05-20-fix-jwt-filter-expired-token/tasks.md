# Tasks: Fix JWT filter expired token 401

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~50-80 |
| 400-line budget risk | Low |
| Chained PRs recommended | No |
| Suggested split | Single PR |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: Yes
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Low

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Fix expired token handling + integration tests | PR 1 → develop | Single atomic fix + tests |

## Phase 1: Test first (RED)

- [x] 1.1 Create `JwtAuthenticationFilterExpiredTokenTest.java` — 5 integration tests: expired token on public (not 401), protected (401), valid token (200), no token (200), triangulation (not 403). Same pattern as `SecurityConfigProductAccessTest`: `@SpringBootTest(RANDOM_PORT)`, `@AutoConfigureMockMvc`, `@ActiveProfiles("test")`, own `@Container PostgreSQLContainer`. Generate expired token via `Jwts.builder()` with past `exp` using same HMAC key
- [x] 1.2 Run new test — confirm RED: expired token on `GET /api/v1/products` returns 401 (bug reproduces)
- [x] 1.3 Commit: `test(security): add expired token filter integration test`

## Phase 2: Fix (GREEN)

- [x] 2.1 In `JwtAuthenticationFilter.java` — remove `final String username` declaration at line 43; move `jwtService.extractUsername(jwt)` + `loadUserByUsername` + `isTokenValid` inside existing try-catch; wrap username extraction and validation in a single guarded block
- [x] 2.2 Run all 5 new tests — confirm GREEN: expired public → not 401, expired protected → 401, valid token → 200, no token → 200
- [x] 2.3 Commit: `fix(security): handle expired JWT tokens gracefully in filter`

## Phase 3: Regression & Push

- [x] 3.1 Run `mvn clean test` — all existing tests pass (regression guard for valid token flow and permitAll access)
- [x] 3.2 Push branch to remote
