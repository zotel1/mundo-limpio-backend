# Tasks: fix/error-handling-core

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~190 |
| 400-line budget risk | Low |
| Chained PRs recommended | No |
| Suggested split | Single PR |
| Delivery strategy | ask-on-risk |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: size-exception
400-line budget risk: Low

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Full fix: impl + unit + integration tests | PR 1 | Single PR, <200 lines, no split needed |

## Phase 1: AuthService — TDD (test + impl)

- [x] 1.1 (RED) `AuthServiceTest.java`: test `login()` mocks `findByEmail`→`Optional.empty()`, asserts `UsernameNotFoundException`
- [x] 1.2 (GREEN) `AuthService.java` line 128: change `new RuntimeException("User not found")` → `new UsernameNotFoundException("User not found after authentication: " + request.email())`

## Phase 2: GlobalExceptionHandler — TDD (tests + impl)

- [x] 2.1 (RED) `GlobalExceptionHandlerTest.java` (new): test `AuthenticationException` mock → 401 + code `INVALID_CREDENTIALS`
- [x] 2.2 (RED) `GlobalExceptionHandlerTest.java`: test `HttpMessageNotReadableException` mock → 400 + code `MALFORMED_JSON`
- [x] 2.3 (GREEN) `GlobalExceptionHandler.java`: add import + `@ExceptionHandler(AuthenticationException.class)` after `OptimisticLockingFailureException` (línea 239), antes del catch-all
- [x] 2.4 (GREEN) `GlobalExceptionHandler.java`: add import + `@ExceptionHandler(HttpMessageNotReadableException.class)` después del handler de AuthenticationException

## Phase 3: Integration Tests (MockMvc)

- [x] 3.1 `AuthControllerTest.java`: mock `authService.login()` → `BadCredentialsException`, POST `/api/v1/auth/login` → 401 + `INVALID_CREDENTIALS`
- [x] 3.2 `AuthControllerTest.java`: POST `/api/v1/auth/register` con body malformado (`{"email": "test", "password":}`) → 400 + `MALFORMED_JSON` (Jackson falla antes de llegar al mock)
- [x] 3.3 `AuthControllerTest.java`: mock `authService.register()` ok, POST con JSON válido → 201 (regresión)
