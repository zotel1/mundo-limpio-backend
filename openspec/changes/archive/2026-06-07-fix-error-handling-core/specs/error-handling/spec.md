# Error Handling — Delta Spec

> Change: `fix/error-handling-core`
> No existing error-handling spec → full spec capturing current + new behavior

## Purpose

Estandarizar respuestas de error para 3 gaps donde excepciones controlables caían al catch-all
(`Exception.class`) y devolvían 500 en vez del código HTTP correcto.

---

## ADDED Requirements

### Requirement: AuthenticationException → 401 UNAUTHORIZED

The `GlobalExceptionHandler` MUST handle `org.springframework.security.core.AuthenticationException`
(and all its subtypes) returning HTTP 401 with code `INVALID_CREDENTIALS`.

**Cubre**: `BadCredentialsException`, `DisabledException`, `LockedException`,
`CredentialsExpiredException`, `InsufficientAuthenticationException`.

La respuesta SHALL ser: `ErrorResponse("INVALID_CREDENTIALS", "Invalid email or password", timestamp, path)`.

#### Scenario: Login con contraseña incorrecta

- GIVEN POST `/api/v1/auth/login` con "test@example.com" y una contraseña inválida
- WHEN el request se procesa
- THEN status 401 y body: `code = "INVALID_CREDENTIALS"`, `message = "Invalid email or password"`

#### Scenario: Login con email inexistente

- GIVEN POST `/api/v1/auth/login` con un email no registrado y cualquier contraseña
- WHEN el request se procesa
- THEN status 401 y body: `code = "INVALID_CREDENTIALS"`

#### Scenario: Endpoint protegido sin autenticación

- GIVEN POST `/api/v1/products` sin JWT token
- WHEN el request se procesa
- THEN status 401
- NOTE: Puede ser interceptado por `HttpStatusEntryPoint` antes del handler — el resultado es el mismo.

### Requirement: HttpMessageNotReadableException → 400 BAD_REQUEST

The `GlobalExceptionHandler` MUST handle `org.springframework.http.converter.HttpMessageNotReadableException`
returning HTTP 400 BAD_REQUEST with code `MALFORMED_JSON`.

La respuesta SHALL ser: `ErrorResponse("MALFORMED_JSON", "Invalid JSON format: <detalle>", timestamp, path)`.

#### Scenario: Registro con JSON malformado

- GIVEN POST `/api/v1/auth/register` con body `{"email": "test@test.com", "password":}`
- WHEN el request se procesa
- THEN status 400 y body: `code = "MALFORMED_JSON"`, `message` contiene el detalle de Jackson

#### Scenario: Login con JSON truncado

- GIVEN POST `/api/v1/auth/login` con body truncado (ej: `{"email": "test"`)
- WHEN el request se procesa
- THEN status 400 y body: `code = "MALFORMED_JSON"`

---

## MODIFIED Requirements

### Requirement: AuthService.login() exception on missing user

When `findByEmail()` returns empty after a successful `authenticationManager.authenticate()`,
the system MUST throw `UsernameNotFoundException` instead of `new RuntimeException(...)`.

(Previously: `throw new RuntimeException("User not found")` on line 128)

`UsernameNotFoundException` is already imported in `AuthService.java` (line 19) and extends
`AuthenticationException`, so it is automatically caught by the `AuthenticationException` handler
returning 401 `INVALID_CREDENTIALS`. No new exception class or constructor is needed.

#### Scenario: Login exitoso (sin cambios)

- GIVEN POST `/api/v1/auth/login` con email y contraseña válidos
- WHEN el request se procesa
- THEN status 200 con access token y refresh token válidos

#### Scenario: Race condition — usuario eliminado entre authenticate() y findByEmail()

- GIVEN un usuario que pasa `authenticationManager.authenticate()` pero es eliminado antes de `findByEmail()`
- WHEN el flujo llega a `findByEmail()` y retorna `Optional.empty()`
- THEN SHALL lanzar `UsernameNotFoundException` con mensaje conteniendo el email
- AND el handler de `AuthenticationException` devuelve `code = "INVALID_CREDENTIALS"`, status 401 (no 500)

---

## API Contracts

### ErrorResponse (sin cambios)

```json
{
  "code": "INVALID_CREDENTIALS",
  "message": "Invalid email or password",
  "timestamp": "2026-06-07T21:30:00",
  "path": "/api/v1/auth/login"
}
```

| Código | HTTP | Cuándo |
|--------|------|--------|
| `INVALID_CREDENTIALS` | 401 | Credenciales inválidas, usuario disabled/locked/expired, auth insuficiente, race condition en login |
| `MALFORMED_JSON` | 400 | Request body con JSON inválido |

---

## Acceptance Criteria

- [ ] POST login con contraseña incorrecta → 401 INVALID_CREDENTIALS
- [ ] POST login con email inexistente → 401 INVALID_CREDENTIALS
- [ ] POST register con JSON malformado → 400 MALFORMED_JSON
- [ ] POST login con JSON truncado → 400 MALFORMED_JSON
- [ ] Race condition en login → 401 INVALID_CREDENTIALS (via AuthenticationException handler)
- [ ] Login exitoso sigue funcionando → 200 OK con tokens
- [ ] `mvn clean verify` pasa sin errores

---

## Testing Notes

| Tipo | Scope | Herramienta |
|------|-------|-------------|
| Unit | Cada nuevo `@ExceptionHandler` invocado directamente con la excepción y `mock(WebRequest)` | JUnit 5 + Mockito |
| Unit | `AuthService.login()` mockea `findByEmail` → `Optional.empty()` y verifica `UsernameNotFoundException` | JUnit 5 + Mockito |
| Integ | End-to-end login con credenciales inválidas vía MockMvc | Spring Test + Testcontainers |
| Integ | End-to-end POST con body malformado vía MockMvc | Spring Test + Testcontainers |
| Regen | Todos los handlers existentes siguen funcionando | Ejecutar test suite completa |
