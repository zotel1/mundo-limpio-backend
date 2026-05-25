# Tasks: Fix SecurityConfig product path mismatch

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~50 (1 modified + 1 new file) |
| 400-line budget risk | Low |
| Chained PRs recommended | No |
| Suggested split | Single PR |
| Delivery strategy | ask-on-risk |

## Phase 1: RED — Write failing test

- [x] 1.1 Create `SecurityConfigProductAccessTest.java` — standalone `@SpringBootTest(RANDOM_PORT)` + `@Testcontainers` integration test with 5 test methods proving product endpoints return 200/201/204 without auth (PROD-ACCESS-001, PROD-ACCESS-002)
- [x] 1.2 Run the test against current code — confirm RED (401 on product endpoints despite permitAll config)

## Phase 2: GREEN — Fix the path

- [x] 2.1 Edit `SecurityConfig.java:75` — replace `/api/v1/product/**` → `/api/v1/products/**` (single AntPathMatcher pattern fix)

## Phase 3: VERIFY — No regressions

- [x] 3.1 Run full test suite — confirm ProductControllerIT, SecurityConfigActuatorTest, and all existing tests pass unchanged

## Phase 4: COMMIT

- [x] 4.1 Commit with conventional message: `fix(security): correct product endpoint path from singular to plural`

## Implementation Order

TDD sequence: RED (test proves bug) → GREEN (one-line fix) → VERIFY (full suite) → COMMIT. No parallel work — each task depends on the previous.

---

*Archived from Engram artifact `sdd/fix-security-config-path/tasks` (ID: #311)*
