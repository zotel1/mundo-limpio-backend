# Error Handling

## Purpose

Estandarizar respuestas de error en toda la API, garantizando que excepciones controlables
devuelvan el código HTTP correcto en vez de caer al catch-all (500).

---

## Capabilities

### Requirement: AuthenticationException → 401 UNAUTHORIZED

The `GlobalExceptionHandler` MUST handle `org.springframework.security.core.AuthenticationException`
(and all its subtypes) returning HTTP 401 with code `INVALID_CREDENTIALS`.

**Cubre**: `BadCredentialsException`, `DisabledException`, `LockedException`,
`CredentialsExpiredException`, `InsufficientAuthenticationException`, `UsernameNotFoundException`.

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

#### Scenario: Race condition — usuario eliminado entre authenticate() y findByEmail()

- GIVEN un usuario que pasa `authenticationManager.authenticate()` pero es eliminado antes de `findByEmail()`
- WHEN el flujo llega a `findByEmail()` y retorna `Optional.empty()`
- THEN SHALL lanzar `UsernameNotFoundException` con mensaje conteniendo el email
- AND el handler de `AuthenticationException` devuelve `code = "INVALID_CREDENTIALS"`, status 401

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

## API Contracts

### ErrorResponse

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
