# Proposal: fix/error-handling-core

## Intent

Eliminar 3 gaps críticos donde excepciones controlables caen al catch-all de `GlobalExceptionHandler` y devuelven 500 en vez del código HTTP correcto. Esto afecta login con credenciales inválidas (debería ser 401), JSON malformado en requests (debería ser 400), y un `RuntimeException` genérico en `AuthService.login()`.

## Scope

### In Scope
1. **BadCredentialsException** → 401 UNAUTHORIZED con código `INVALID_CREDENTIALS`
2. **HttpMessageNotReadableException** → 400 BAD_REQUEST con código `MALFORMED_JSON`
3. **AuthService.login() RuntimeException** → excepción específica `UserNotFoundException` (ya existe)

### Out of Scope
- Otros gaps de error handling (AuthExceptionHandler y GlobalExceptionHandler cubren el resto)
- Tests de integración para estos nuevos handlers (se agregan en la fase apply)
- Refactor del `ErrorResponse` record (se queda como está)
- Manejo de `AuthenticationException` genérica (solo `BadCredentialsException` por ahora)

## Capabilities

### New Capabilities
None — bug fix, no new capability specs needed.

### Modified Capabilities
None — no existing specs change behavior.

## Approach

| # | Problema | Solución | Archivo |
|---|----------|----------|---------|
| 1 | `BadCredentialsException` → 500 | Agregar `@ExceptionHandler` en `GlobalExceptionHandler` que retorne 401 con `INVALID_CREDENTIALS` | `GlobalExceptionHandler.java` |
| 2 | `HttpMessageNotReadableException` → 500 | Agregar `@ExceptionHandler` en `GlobalExceptionHandler` que retorne 400 con `MALFORMED_JSON` | `GlobalExceptionHandler.java` |
| 3 | `new RuntimeException("User not found")` | Reemplazar por `throw new UserNotFoundException(email)` — la clase ya existe, solo falta un constructor que acepte String | `AuthService.java` (línea 128), `UserNotFoundException.java` |

Las excepciones nuevas se agregan ANTES del catch-all `Exception.class` en `GlobalExceptionHandler`. El orden de los handlers no importa (Spring los resuelve por tipo exacto), pero por claridad se agregan después de `IllegalArgumentException` y antes del catch-all.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `common/handler/GlobalExceptionHandler.java` | Modified | +2 handlers (BadCredentials, HttpMessageNotReadable) |
| `user/service/AuthService.java` | Modified | línea 128: RuntimeException → UserNotFoundException |
| `user/exception/UserNotFoundException.java` | Modified | +constructor que acepte String (email) |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| `BadCredentialsException` sea capturada antes de llegar al handler (por Spring Security filter chain) | Low | SecurityConfig usa `HttpStatusEntryPoint(401)` para requests no autenticados, pero `BadCredentialsException` ocurre **después** de la autenticación (en el controller flow), así que el handler sí la recibe |
| `HttpMessageNotReadableException` tenga subtipos que requieran manejo distinto | Low | Por ahora un solo handler genérico cubre todos los parse errors de Jackson |

## Rollback Plan

Revertir los cambios en los 3 archivos con `git revert`. El cambio es pequeño y atómico.

## Dependencies

- Ninguna. Todos los cambios son dentro del proyecto existente sin nuevas dependencias.

## Success Criteria

- [ ] Login con email+password incorrectos → 401 con `INVALID_CREDENTIALS`
- [ ] POST/PUT con body JSON malformado → 400 con `MALFORMED_JSON`
- [ ] Login con email válido pero usuario eliminado entre auth y fetch → 404 con `USER_NOT_FOUND`
- [ ] `mvn clean verify` pasa sin errores
