# Error Handling — Delta Spec

> Change: `fix/error-handling-consistency`

## Purpose

Unificar el formato de error en toda la API usando `ErrorResponse` y cubrir 4 gaps HTTP que actualmente caen al catch-all (500).

---

## ADDED Requirements

### Requirement: 3 module handlers retornan ErrorResponse

ProductionBatchExceptionHandler, BulkProductExceptionHandler y ReceiptExceptionHandler MUST retornar `ResponseEntity<ErrorResponse>` en vez de `Map<String,Object>`. Códigos HTTP y error codes no cambian.

| Handler | HTTP | Error Code | Campos faltantes |
|---------|------|-----------|------------------|
| ProductionBatchExceptionHandler | 404 | `PRODUCTION_BATCH_NOT_FOUND` | `path` |
| BulkProductExceptionHandler | 404 | `BULK_PRODUCT_NOT_FOUND` | `path` |
| ReceiptExceptionHandler | 422 | `OCR_PROCESSING_ERROR` | Ninguno |

#### Scenario: Cada handler retorna ErrorResponse con path y timestamp

- GIVEN una excepción del módulo respectivo (ProductionBatchNotFound, BulkProductNotFound, OcrProcessingError)
- WHEN el handler la captura
- THEN status HTTP y error code se mantienen, body incluye `timestamp` ISO-8601 y `path` del request

### Requirement: Rate limit 429 con timestamp y path

RateLimitFilter MUST incluir `timestamp` y `path` en la response JSON 429. Debe MANTENER el campo `retryAfterSeconds`.

#### Scenario: 429 incluye todos los campos

- GIVEN 11 requests/min a `/api/v1/auth/login` (límite: 10/min)
- WHEN el 11° request se procesa
- THEN status 429, body incluye `code = "RATE_LIMIT_EXCEEDED"`, `timestamp`, `path`, y `retryAfterSeconds = N`

### Requirement: SaleController 404 con ErrorResponse body

SaleController.getSaleById() MUST retornar 404 con `ErrorResponse` body cuando NoSuchElementException ocurre.

#### Scenario: Venta no encontrada

- GIVEN GET `/api/v1/sales/9999` y venta no existe
- WHEN NoSuchElementException se lanza
- THEN status 404, body: `code = "SALE_NOT_FOUND"`, `message` contiene el id

### Requirement: 4 nuevos handlers HTTP en GlobalExceptionHandler

GlobalExceptionHandler MUST handle estas 4 excepciones (hoy caen al catch-all 500):

| Spring Exception | HTTP | Código | Mensaje |
|------------------|------|--------|---------|
| `HttpRequestMethodNotSupportedException` | 405 | `METHOD_NOT_ALLOWED` | "Request method '{method}' is not supported for this endpoint" |
| `HttpMediaTypeNotSupportedException` | 415 | `UNSUPPORTED_MEDIA_TYPE` | "Content-Type '{contentType}' is not supported" |
| `MissingServletRequestParameterException` | 400 | `MISSING_PARAMETER` | "Required parameter '{name}' is missing" |
| `ConstraintViolationException` | 400 | `VALIDATION_ERROR` | Violaciones concatenadas |

#### Scenario: POST a endpoint solo-GET → 405

- GIVEN GET-only endpoint como `/api/v1/sales/{id}`
- WHEN POST request se envía
- THEN status 405, `code = "METHOD_NOT_ALLOWED"`

#### Scenario: Content-Type text/xml → 415

- GIVEN POST `/api/v1/products` con `Content-Type: text/xml`
- WHEN request se procesa
- THEN status 415, `code = "UNSUPPORTED_MEDIA_TYPE"`

#### Scenario: Parámetro query obligatorio faltante → 400

- GIVEN GET `/api/v1/sales?sort=date` sin `page` (required)
- WHEN request se procesa
- THEN status 400, `code = "MISSING_PARAMETER"`

#### Scenario: Violación de constraint → 400

- GIVEN POST con `@Positive int quantity = -1` y `@Validated`
- WHEN validación falla
- THEN status 400, `code = "VALIDATION_ERROR"`

---

## API Contracts

### ErrorResponse (standard)

```json
{
  "code": "PRODUCTION_BATCH_NOT_FOUND",
  "message": "Production batch not found with id: 9999",
  "timestamp": "2026-06-07T22:00:00",
  "path": "/api/v1/production-batches/9999"
}
```

### RateLimitErrorResponse (con retryAfterSeconds)

```json
{
  "code": "RATE_LIMIT_EXCEEDED",
  "message": "Demasiadas requests. Intenta de nuevo en 6 segundos.",
  "timestamp": "2026-06-07T22:00:00",
  "path": "/api/v1/auth/login",
  "retryAfterSeconds": 6
}
```

### Códigos agregados

| Código | HTTP | Cuándo |
|--------|------|--------|
| `PRODUCTION_BATCH_NOT_FOUND` | 404 | Batch no encontrado |
| `BULK_PRODUCT_NOT_FOUND` | 404 | Producto bulk no encontrado |
| `OCR_PROCESSING_ERROR` | 422 | Error OCR |
| `RATE_LIMIT_EXCEEDED` | 429 | Rate limit excedido |
| `SALE_NOT_FOUND` | 404 | Venta no encontrada |
| `METHOD_NOT_ALLOWED` | 405 | Método no soportado |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | Content-Type no soportado |
| `MISSING_PARAMETER` | 400 | Parámetro faltante |
| `VALIDATION_ERROR` | 400 | Violación de constraint |

---

## Acceptance Criteria

- [ ] 3 module handlers retornan `ErrorResponse` con `timestamp`+`path`
- [ ] Rate limit 429 incluye `timestamp`, `path`, `retryAfterSeconds`
- [ ] GET `/api/v1/sales/{id}` inexistente → 404 con body `SALE_NOT_FOUND`
- [ ] Método no soportado → 405 `METHOD_NOT_ALLOWED`
- [ ] Content-Type inválido → 415 `UNSUPPORTED_MEDIA_TYPE`
- [ ] Parámetro faltante → 400 `MISSING_PARAMETER`
- [ ] Constraint violation → 400 `VALIDATION_ERROR`
- [ ] Handlers existentes (auth, JSON) siguen funcionando sin cambios
- [ ] `mvn clean verify` pasa sin errores

---

## Testing Notes

| Tipo | Scope | Herramienta |
|------|-------|-------------|
| Unit | 3 module handlers + 4 new HTTP handlers + SaleController 404 | JUnit 5 + Mockito |
| Integ | RateLimitFilter response vía MockMvc | Spring Test |
| Integ | End-to-end 405/415/400 via MockMvc | Spring Test + Testcontainers |
| Regen | Suite completa sin regresiones | `mvn clean verify` |

---

## Open Question

**RateLimitFilter**: ¿cómo incluir `retryAfterSeconds`?

- **A**: Extender `ErrorResponse` con `@JsonInclude(NON_NULL) Integer retryAfterSeconds` — campo opcional null en errores normales.
- **B**: `ErrorResponse` estándar + header HTTP `Retry-After` — frontend debe leer header.
- **C**: `RateLimitErrorResponse` record separado que duplica campos de ErrorResponse + `retryAfterSeconds` — sin acoplamiento.
