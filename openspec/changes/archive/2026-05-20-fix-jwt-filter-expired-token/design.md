# Design: Fix JWT filter expired token 401

## Technical Approach

Single-line scope fix: move `jwtService.extractUsername(jwt)` from line 51 (outside try-catch) into the existing try-catch block. Wrap `username` declaration and the `loadUserByUsername` + `isTokenValid` flow in the same guarded block. All jjwt exceptions (ExpiredJwtException, MalformedJwtException, SignatureException) become silent catches — filter continues to `filterChain.doFilter()` and Spring Security authorization rules decide access.

## Architecture Decisions

| Option | Tradeoff | Decision |
|--------|----------|----------|
| Move 1 line inside existing try-catch | Minimal diff, keeps existing catch semantics | **Chosen** — zero risk of regression |
| Wrap only extractUsername in its own try-catch | More code, re-declares username outside scope | Rejected — verbose, same result |
| New dedicated JwtException.class catch block | Over-engineered for a 1-line fix | Rejected — YAGNI |
| Create separate ExpiredTokenFilter | Adds filter chain complexity | Rejected — overkill |

## Data Flow

### Before (broken)

```
Request → JwtAuthenticationFilter
  ├─ authHeader == null? → filterChain (OK)
  ├─ extractUsername(jwt) ← OUTSIDE try-catch
  │   └─ parseSignedClaims() throws ExpiredJwtException → 💥 401 (UNCAUGHT)
  └─ try { loadUser, validate, setAuth } catch {} ← NEVER REACHED for expired tokens
```

### After (fixed)

```
Request → JwtAuthenticationFilter
  ├─ authHeader == null? → filterChain (OK)
  └─ try {
       extractUsername(jwt)
       ├─ parseSignedClaims() throws → caught silently
       └─ OK → loadUser → validate → setAuth
     } catch (Exception e) { /* silent */ }
  → filterChain.doFilter() ALWAYS reached
  → Spring Security authorization rules decide response
```

Public endpoints (`/api/v1/products`, `/api/v1/auth/**`) still pass through filter chain. With no SecurityContext set (expired token), `.permitAll()` rules apply → 200 OK. Protected endpoints fail `.authenticated()` check → 401 via `HttpStatusEntryPoint`.

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `src/main/java/.../filter/JwtAuthenticationFilter.java` | Modify | Move `extractUsername` + `loadUserByUsername` inside try-catch; remove `final` from `username` |
| `src/test/java/.../filter/JwtAuthenticationFilterExpiredTokenTest.java` | Create | 5 integration tests for expired/valid/absent tokens on public & protected endpoints |

## Exact Diff (JwtAuthenticationFilter.java)

```java
// === BEFORE (lines 41-69) ===
final String authHeader = request.getHeader("Authorization");
final String jwt;
final String username;                          // ← final removed in fix

if (authHeader == null || !authHeader.startsWith("Bearer ")) {
    filterChain.doFilter(request, response);
    return;
}

jwt = authHeader.substring(7);
username = jwtService.extractUsername(jwt);     // ← LINE 51: OUTSIDE try-catch

if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
    try {
        UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);
        if (jwtService.isTokenValid(jwt, userDetails)) {
            UsernamePasswordAuthenticationToken authToken = ...;
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }
    } catch (Exception e) { /* silent */ }
}
filterChain.doFilter(request, response);

// === AFTER ===
final String authHeader = request.getHeader("Authorization");
final String jwt;
// username declared inside try block

if (authHeader == null || !authHeader.startsWith("Bearer ")) {
    filterChain.doFilter(request, response);
    return;
}

jwt = authHeader.substring(7);

if (SecurityContextHolder.getContext().getAuthentication() == null) {
    try {
        String username = jwtService.extractUsername(jwt);  // ← INSIDE try-catch
        if (username != null) {
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);
            if (jwtService.isTokenValid(jwt, userDetails)) {
                UsernamePasswordAuthenticationToken authToken = ...;
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
    } catch (Exception e) { /* silent */ }
}
filterChain.doFilter(request, response);
```

## Test Strategy

New file: `src/test/java/com/mundolimpio/application/security/filter/JwtAuthenticationFilterExpiredTokenTest.java`

Follows `SecurityConfigProductAccessTest` pattern: `@SpringBootTest(RANDOM_PORT)` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")` + `@Testcontainers` + own `@Container PostgreSQLContainer`. Uses MockMvc with real Bearer tokens sent through the full filter chain.

Generate an expired token in `@BeforeAll`:
```java
static String expiredToken;

@BeforeAll
static void setup() {
    byte[] keyBytes = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970".getBytes();
    SecretKey key = Keys.hmacShaKeyFor(keyBytes);
    expiredToken = Jwts.builder()
        .subject("test@test.com")
        .issuedAt(new Date(System.currentTimeMillis() - 86_400_000)) // 1 day ago
        .expiration(new Date(System.currentTimeMillis() - 43_200_000)) // 12h ago
        .signWith(key)
        .compact();
}
```

### Test Methods

| # | Method | Request | Status | Why |
|---|--------|---------|--------|-----|
| 1 | `publicEndpoint_WithExpiredToken_ShouldNotReturn401` | `GET /api/v1/products` + expired Bearer | isNot(401) | Core bug fix — JWT-FILTER-001 |
| 2 | `publicEndpoint_WithExpiredToken_ShouldNotReturn403` | Same | isNot(403) | Triangulation — token is simply ignored |
| 3 | `protectedEndpoint_WithExpiredToken_ShouldReturn401` | `GET /api/v1/sales` + expired Bearer | isUnauthorized() | Regression guard — JWT-FILTER-002 |
| 4 | `publicEndpoint_WithValidToken_ShouldWork` | `GET /api/v1/products` + valid Bearer | isOk() | Regression — valid tokens still work |
| 5 | `publicEndpoint_WithoutToken_ShouldWork` | `GET /api/v1/products` no auth header | isOk() | Regression — null header still works (JWT-FILTER-004) |

For test #4 (valid token): register a user via `POST /api/v1/auth/register`, get a real token, or generate one with 1-hour future expiration using the same sign key.

## Edge Cases (already handled by existing code)

| Case | Filter behavior | Status |
|------|----------------|--------|
| `Authorization` header missing | `filterChain.doFilter()` at line 46 — short-circuit return | Already handled |
| `Authorization: Bearer ` (empty token) | `authHeader.startsWith("Bearer ")` is true, but empty token → `parseSignedClaims` throws → caught by try-catch | Fixed by this change |
| Malformed JWT (not 3 parts) | `parseSignedClaims` throws MalformedJwtException → caught | Fixed by this change |
| Tampered JWT (wrong signature) | `parseSignedClaims` throws SignatureException → caught | Fixed by this change |
| Null auth header + protected endpoint | No auth context set → `HttpStatusEntryPoint` → 401 | No change needed |

Before the fix, all the above edge cases that hit `parseSignedClaims` also propagated uncaught (same root cause). The fix resolves ALL of them at once.

## Rollback

```bash
git revert HEAD   # single commit, ~10-line diff in JwtAuthenticationFilter.java
```

Or manual: move `extractUsername(jwt)` back above the try-catch, restore `final String username;` declaration.

## Dependencies

None — no new libraries, no config changes, no database migrations. Pure Java refactor of existing code.
