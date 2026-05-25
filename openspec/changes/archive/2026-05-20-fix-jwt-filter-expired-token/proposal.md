# Proposal: Fix JWT filter expired token 401 bug

## Intent

`JwtAuthenticationFilter` crashes with 401 when ANY request carries an expired JWT — even on `permitAll` endpoints like `/api/v1/auth/register`. Root cause: `jwtService.extractUsername(jwt)` on line 51 runs OUTSIDE the try-catch block. The jjwt library's `parseSignedClaims()` throws `ExpiredJwtException` uncaught, Spring Security returns 401. Flutter app sends expired tokens on all requests (SharedPreferences interceptor), making public endpoints unusable after token expiry.

## Scope

### In Scope
- Move `extractUsername()` inside existing try-catch block in `JwtAuthenticationFilter.java`
- Remove `final` qualifier from `username` variable (or make it local to try block)
- Add test: expired Bearer token on public endpoint does NOT cause 401

### Out of Scope
- JWT refresh token rotation (separate feature)
- Token revocation / blacklist
- Flutter-side token expiry handling

## Capabilities

### New Capabilities
None — behavioral fix, no new spec-level capability.

### Modified Capabilities
None — no contract-level requirement changes. Existing permitAll semantics are already correct; implementation was buggy.

## Approach

Restructure `JwtAuthenticationFilter.doFilterInternal()` so the entire token validation flow (extract → load → validate) is inside a single try-catch. This makes all jjwt exceptions (expired, malformed, tampered) safe — the filter continues to `filterChain.doFilter()` and lets Spring Security authorization rules decide access.

### Current (broken)
```java
jwt = authHeader.substring(7);
username = jwtService.extractUsername(jwt);   // ← LINE 51: THROWS, uncaught!

if (username != null && ...) {
    try {  // ← too late, we never get here
        ...
    } catch (Exception e) { }
}
```

### Fix
```java
jwt = authHeader.substring(7);
String username = null;
try {
    username = jwtService.extractUsername(jwt);
    if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
        UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);
        if (jwtService.isTokenValid(jwt, userDetails)) {
            ...
        }
    }
} catch (Exception e) {
    // Silently swallow — let SecurityConfig decide access
}
```

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `src/main/java/.../filter/JwtAuthenticationFilter.java` | Modified | Move 1 line, wrap in try-catch |
| `src/test/java/.../filter/JwtAuthenticationFilterExpiredTokenTest.java` | New | Integration test with expired JWT |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| `username` effectively null on expired token → `loadUserByUsername` skipped | Low — intended | No auth context set for expired tokens; filter falls through to security rules |
| Silent catch hides real parsing errors | Low — existing catch already hides all exceptions | Same pattern, no behavior change for non-expired tokens |
| Flutter caches expired token indefinitely | Med — separate issue | Out of scope; token refresh feature in backlog |

## Rollback Plan

Revert the single-line move in `JwtAuthenticationFilter.java` and delete the test file. `git revert HEAD` — the diff is under 10 lines.

## Dependencies

None — pure Java/Spring change. No new libraries, no config changes, no migrations.

## Success Criteria

- [ ] `GET /api/v1/auth/register` with expired Bearer token returns 409 (or 201), NOT 401
- [ ] `POST /api/v1/products` with expired Bearer token returns expected response (not 401)
- [ ] Valid JWT still works on protected endpoints (regression guard)
- [ ] No JWT in header on public endpoint still works (null/absent header guard)
