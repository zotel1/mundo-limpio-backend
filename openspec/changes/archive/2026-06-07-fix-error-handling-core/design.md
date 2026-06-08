# Design: fix/error-handling-core

## Technical Approach

Agregar 2 `@ExceptionHandler` en `GlobalExceptionHandler` para capturar `AuthenticationException` (→ 401) y
`HttpMessageNotReadableException` (→ 400) antes de que caigan al catch-all de `Exception.class` que devuelve 500.
Reemplazar `RuntimeException` en `AuthService.login()` línea 128 por `UsernameNotFoundException` (ya importado),
que al extender `AuthenticationException` será capturado por el nuevo handler → 401.

## Architecture Decisions

### Decision: AuthenticationException en vez de BadCredentialsException

| Opción | Tradeoff | Decisión |
|--------|----------|----------|
| `BadCredentialsException` | Solo cubre 1 subtipo — otros (Disabled, Locked, etc.) siguen yendo a 500 | ❌ |
| `AuthenticationException` | Cubre **todos** los subtipos con 1 handler (BadCredentials, Disabled, Locked, CredentialsExpired, InsufficientAuthentication, UsernameNotFoundException) | ✅ |

**Rationale**: El handler de `AuthenticationException` es polimórfico: captura cualquier subtipo, incluyendo
el `UsernameNotFoundException` del Fix 3. Es la solución más escalable.

### Decision: UsernameNotFoundException en AuthService.login()

| Opción | Tradeoff | Decisión |
|--------|----------|----------|
| `UserNotFoundException(String)` (spec) | Ya existe, handler existente → 404 USER_NOT_FOUND. Semánticamente correcto para race condition (credenciales válidas, usuario eliminado). Pero requiere agregar constructor String. | ❌ |
| `UsernameNotFoundException` (orquestador) | Ya importado (línea 19), extiende `AuthenticationException` → 401 INVALID_CREDENTIALS. Cero código nuevo, solo cambiar línea 128. | ✅ |

**Rationale**: Sigo la instrucción del orquestador. `UsernameNotFoundException` ya se usa en `refresh()` (línea 176)
del mismo archivo. **Nota**: el status HTTP resultante es 401 (no 404 como dice la spec) — ver Open Questions.

### Decision: Orden de los handlers en GlobalExceptionHandler

Los nuevos handlers se agregan después del bloque de `OptimisticLockingFailureException` (línea 239) y antes
del catch-all `Exception.class` (línea 245). Spring resuelve por tipo exacto, no por orden, pero por
claridad se ubican junto a los otros handlers de seguridad/formato.

## Data Flow

### Fix 1: Login con credenciales inválidas

```
POST /api/v1/auth/login
  → AuthController.login()
    → AuthService.login()
      → authenticationManager.authenticate() → BadCredentialsException ↑
      → GlobalExceptionHandler @ExceptionHandler(AuthenticationException.class)
    ← 401 { code: "INVALID_CREDENTIALS", message: "Invalid email or password" }
```

### Fix 2: Request body con JSON malformado

```
POST /api/v1/auth/register
  → Spring HttpMessageConverter (Jackson) → parse error
  → HttpMessageNotReadableException ↑
  → GlobalExceptionHandler @ExceptionHandler(HttpMessageNotReadableException.class)
← 400 { code: "MALFORMED_JSON", message: "Invalid JSON format: <detalle Jackson>" }
```

### Fix 3: Race condition en login

```
POST /api/v1/auth/login
  → AuthController.login()
    → AuthService.login()
      → authenticationManager.authenticate() → ✅ pasa (usuario existe)
      → userRepository.findByEmail(request.email()) → Optional.empty() (eliminado entre medio)
      → throw new UsernameNotFoundException(...) ↑
      → GlobalExceptionHandler @ExceptionHandler(AuthenticationException.class)
    ← 401 { code: "INVALID_CREDENTIALS", message: "Invalid email or password" }
```

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `src/main/java/com/mundolimpio/application/common/handler/GlobalExceptionHandler.java` | Modify | +2 imports (`AuthenticationException`, `HttpMessageNotReadableException`), +2 `@ExceptionHandler` methods antes del catch-all |
| `src/main/java/com/mundolimpio/application/user/service/AuthService.java` | Modify | Línea 128: `RuntimeException` → `UsernameNotFoundException`. Sin nuevos imports (ya existe en línea 19) |

## Interfaces / Contracts

```java
// GlobalExceptionHandler — 2 nuevos handlers
@ExceptionHandler(AuthenticationException.class)
ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException, WebRequest);
// → HTTP 401, code: "INVALID_CREDENTIALS", message: "Invalid email or password"

@ExceptionHandler(HttpMessageNotReadableException.class)
ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException, WebRequest);
// → HTTP 400, code: "MALFORMED_JSON", message: "Invalid JSON format: <Jackson detail>"
```

### ErrorResponse outputs

```json
// Fix 1 y 3 → 401
{ "code": "INVALID_CREDENTIALS", "message": "Invalid email or password",
  "timestamp": "2026-06-07T21:30:00", "path": "/api/v1/auth/login" }

// Fix 2 → 400
{ "code": "MALFORMED_JSON", "message": "Invalid JSON format: ...",
  "timestamp": "2026-06-07T21:30:00", "path": "/api/v1/auth/register" }
```

## Testing Strategy

| Layer | What | Approach |
|-------|------|----------|
| Unit | `AuthenticationException` handler | Instanciar `GlobalExceptionHandler`, pasar `mock(AuthenticationException.class)` + `mock(WebRequest)`, verificar 401 + código `INVALID_CREDENTIALS` |
| Unit | `HttpMessageNotReadableException` handler | Instanciar `GlobalExceptionHandler`, pasar `mock(HttpMessageNotReadableException.class)` + `mock(WebRequest)`, verificar 400 + código `MALFORMED_JSON` |
| Unit | `UsernameNotFoundException` en `AuthService.login()` | Mockear `findByEmail` → `Optional.empty()`, verificar que `login()` lanza `UsernameNotFoundException` |
| Integ | Login con credenciales inválidas | `MockMvc` → POST `/api/v1/auth/login` con email+password falsos → 401 + `INVALID_CREDENTIALS` |
| Integ | Register con JSON malformado | `MockMvc` → POST `/api/v1/auth/register` con body `{"email": "test"` (truncado) → 400 + `MALFORMED_JSON` |
| Integ | Login con JSON truncado | `MockMvc` → POST `/api/v1/auth/login` con body truncado → 400 + `MALFORMED_JSON` |
| Regen | Tests existentes | `mvn clean verify` — si algún test esperaba 500 para estos casos, actualizarlo a 401/400 |

**Nota Strict TDD**: Los tests se escriben ANTES del código (RED), luego se implementa (GREEN), se triangula,
y se refactoriza. Seguir el ciclo por cada handler individualmente.

## Migration / Rollout

No migration required. Cambios atómicos en 2 archivos, sin nuevos beans, sin cambios en DB. Rollback con `git revert`.

## Open Questions

- [ ] **Discrepancia Fix 3**: La spec indica usar `UserNotFoundException(email)` → 404 `USER_NOT_FOUND`
  (race condition: credenciales válidas, usuario ya no existe). El orquestador indica `UsernameNotFoundException`
  → 401 `INVALID_CREDENTIALS`. ¿Cuál prevalece? Semánticamente, 404 es más correcto (credenciales OK),
  pero el orquestador pide 401.
- [ ] ¿Agregar el constructor `UserNotFoundException(String)` de todas formas para usos futuros, aunque
  no se use en este cambio?
