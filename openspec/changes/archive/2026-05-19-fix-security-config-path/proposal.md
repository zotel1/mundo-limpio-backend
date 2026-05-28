# Proposal: Fix SecurityConfig product path mismatch

## Intent

SecurityConfig.java permits `/api/v1/product/**` (singular) but ProductController maps to `/api/v1/products` (plural). This means the product catalog — intended to be public — requires authentication because the path patterns never match. Fix the path to restore public access.

## Scope

### In Scope
- Change SecurityConfig.java line 75: `/api/v1/product/**` → `/api/v1/products/**`
- Add `SecurityConfigProductAccessTest.java` verifying public endpoints work without auth
- Confirm POST /api/v1/products is also public (all product endpoints under permitAll)

### Out of Scope
- Other endpoint security audits (deferred)
- Changing ProductController path (it's already correct with plural)

## Capabilities

### New Capabilities
- None — pure bug fix, no new capability introduced.

### Modified Capabilities
- None — behavioral requirements unchanged (product catalog was always intended to be public), only the implementation was wrong.

## Approach

1. Edit SecurityConfig.java: replace `/api/v1/product/**` with `/api/v1/products/**`
2. Write `SecurityConfigProductAccessTest.java` that sends GET /api/v1/products without any auth header and expects 200
3. Verify the test catches the regression (run it against old code first if needed)

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `security/config/SecurityConfig.java:75` | Modified | Path pattern singular→plural |
| `security/config/SecurityConfigProductAccessTest.java` | New | Security integration test |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Other paths with same typo | Low | Scope is single change; test confirms this specific fix |
| Test flakiness (auth context) | Low | Use `@WithMockUser` is not needed — test verifies unauthenticated access explicitly |
| Existing auth tests rely on old path | Low | None exist with that pattern; check with grep before applying |

## Rollback Plan

Revert single line change and delete test file. No schema, migration, or data involved.

## Dependencies

- Java 21 + Spring Boot 3.3.0 test infrastructure (existing)
- No external dependencies

## Success Criteria

- [ ] GET /api/v1/products (list) returns 200 without any auth header
- [ ] GET /api/v1/products/{id} returns 200 without auth header
- [ ] Existing auth tests still pass (no regression)
- [ ] POST /api/v1/products remains accessible (permitAll as configured)

---

*Archived from Engram artifact `sdd/fix-security-config-path/proposal` (ID: #304)*
