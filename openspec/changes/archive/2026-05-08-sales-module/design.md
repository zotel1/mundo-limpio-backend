# Design: Sales Module (Módulo de Ventas)

## Technical Approach

Implementar el módulo de ventas siguiendo EXACTAMENTE el patrón de los módulos anteriores (Product, BulkProduct, ProductionBatch). La clave técnica es la **lógica FIFO** en `SaleService`, usando el método YA EXISTENTE `ProductionBatchRepository.findByProductIdAndCurrentStockGreaterThanOrderByProductionDateAsc()` para obtener lotes ordenados por fecha ascendente (más antiguo primero).

El diseño usa **Strict TDD**: tests se escriben ANTES que el código.

## Architecture Decisions

### Decision: Use Existing FIFO Method (Reuse over Reinvent)

**Choice**: Reutilizar `ProductionBatchRepository.findByProductIdAndCurrentStockGreaterThanOrderByProductionDateAsc()` (ya existe) en lugar de crear nueva query.
**Alternatives considered**: 
- Crear nueva query JPQL personalizada en `SaleRepository`
- Implementar lógica de ordenamiento en el Service

**Rationale**: 
- El método YA ESTÁ TESTEADO en `ProductionBatchServiceTest` y `ProductionBatchControllerIT`
- Sigue el principio **DRY** (Don't Repeat Yourself)
- Menos código nuevo = menos bugs potenciales
- El método específicamente ordena por `productionDate ASC` (exactamente lo que FIFO necesita)

### Decision: Complete Module Structure (Follow Existing Pattern)

**Choice**: Crear estructura completa: `domain/`, `repository/`, `service/`, `controller/`, `dto/`, `mapper/`
**Alternatives considered**: 
- Módulo mínimo (solo Service + Controller)
- Módulo híbrido reusando mappers/repositorios existentes

**Rationale**: 
- Consistencia total con el proyecto (Product, BulkProduct, ProductionBatch)
- Facilita onboarding de nuevos devs (misma estructura en todos lados)
- Separación clara de responsabilidades (SRP del SOLID)
- Permite testing completo (unit + integration)

### Decision: Security - ADMIN Only for MVP

**Choice**: Restringir `POST /api/v1/sales` a role **ADMIN** usando `@PreAuthorize("hasRole('ADMIN')")`
**Alternatives considered**: 
- Permitir también a OPERATOR
- Hacer endpoint público (sin auth)

**Rationale**: 
- Sigue patrón de otros módulos (BulkProduct, ProductionBatch son ADMIN-only)
- Ventas afectan inventario (operación sensible)
- OPERATOR puede tener acceso en futura versión (después de MVP)
- Prevenir fraudes o errores de usuarios sin privilegios

### Decision: Strict TDD (Write Tests First)

**Choice**: Escribir tests ANTES del código (Red → Green → Refactor)
**Alternatives considered**: 
- Escribir código primero, tests después
- Solo integration tests (sin unit tests)

**Rationale**: 
- El proyecto tiene `Strict TDD Mode: enabled` en su configuración
- Garantiza que cada línea de código tenga propósito
- Detecta bugs temprano (antes de escribir implementación)
- Documenta el comportamiento esperado via tests

## Data Flow

### Create Sale Flow (FIFO)

```
Client → SaleController → SaleService → ProductionBatchRepository
                                        ↓
                              ProductionBatch(es) con stock > 0 ordenados por fecha ASC
                                        ↓
                              Iterar lotes restando `currentStock`
                                        ↓
                              Crear Sale + SaleItem(s) en @Transactional
                                        ↓
                              Retornar SaleResponse 201 Created
```

### ASCII Diagram

```
[Client] ──POST /api/v1/sales──→ [SaleController]
         │                              ↓
         │                    [SaleService.createSale()]
         │                              ↓
         │        [ProductionBatchRepository.findWithStockForFifo()]
         │                              ↓
         │                    [Batch A: 20 units] ← Primero (más antiguo)
         │                              ↓
         │                    [Batch B: 30 units] ← Segundo
         │                              ↓
         │                    [Batch C: 50 units] ← No se toca (suficiente con A+B)
         │                              ↓
         │                    [@Transactional] Crear Sale + Items
         │                              ↓
         └───201 Created + SaleResponse─────────────← [Client]
```

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `src/main/java/.../sales/domain/Sale.java` | Create | JPA Entity: id (Long), totalAmount (BigDecimal), createdAt (Instant) |
| `src/main/java/.../sales/domain/SaleItem.java` | Create | JPA Entity: id (Long), sale (Sale), productionBatch (ProductionBatch), quantity (BigDecimal), unitPriceAtSale (BigDecimal), unitCostAtSale (BigDecimal) |
| `src/main/java/.../sales/repository/SaleRepository.java` | Create | `extends JpaRepository<Sale, Long>` |
| `src/main/java/.../sales/repository/SaleItemRepository.java` | Create | `extends JpaRepository<SaleItem, Long>` |
| `src/main/java/.../sales/service/SaleService.java` | Create | Lógica FIFO, `@Transactional`, usa `findAllWithStockForFifo()` |
| `src/main/java/.../sales/controller/SaleController.java` | Create | `@RestController`, `@PreAuthorize("hasRole('ADMIN')")`, `@Tag` para OpenAPI |
| `src/main/java/.../sales/dto/SaleRequest.java` | Create | `record` con `productId` (Long), `quantity` (BigDecimal) + Jakarta Validation |
| `src/main/java/.../sales/dto/SaleResponse.java` | Create | `record` con `id`, `totalAmount`, `createdAt` |
| `src/main/java/.../sales/dto/SaleItemRequest.java` | Create | (No se usa en request, solo response) |
| `src/main/java/.../sales/dto/SaleItemResponse.java` | Create | `record` con `batchId`, `quantity`, `unitPrice`, `unitCost` |
| `src/main/java/.../sales/mapper/SaleMapper.java` | Create | `@Component`, métodos `toEntity()`, `toResponse()` |
| `src/main/java/.../security/config/SecurityConfig.java` | Modify | Agregar `.requestMatchers("/api/v1/sales/**").hasRole("ADMIN")` |
| `src/test/java/.../sales/SaleServiceTest.java` | Create | Unit tests (Strict TDD: escribir ANTES del Service) |
| `src/test/java/.../sales/controller/SaleControllerIT.java` | Create | Integration tests con TestRestTemplate + H2 |

## Interfaces / Contracts

### SaleRequest (DTO - Input)

```java
package com.mundolimpio.application.sales.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SaleRequest(
    @NotNull(message = "Product ID cannot be null")
    Long productId,

    @NotNull(message = "Quantity cannot be null")
    @Positive(message = "Quantity must be greater than zero")
    BigDecimal quantity
) {}
```

### SaleResponse (DTO - Output)

```java
package com.mundolimpio.application.sales.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SaleResponse(
    Long id,
    BigDecimal totalAmount,
    Instant createdAt,
    List<SaleItemResponse> items
) {}
```

### SaleItemResponse (DTO - Output for items)

```java
package com.mundolimpio.application.sales.dto;

import java.math.BigDecimal;

public record SaleItemResponse(
    Long batchId,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal unitCost
) {}
```

### SaleService Method Signature

```java
package com.mundolimpio.application.sales.service;

import com.mundolimpio.application.sales.dto.SaleRequest;
import com.mundolimpio.application.sales.dto.SaleResponse;

@Service
public class SaleService {
    
    @Transactional
    public SaleResponse createSale(SaleRequest request) {
        // 1. Validar stock total disponible
        // 2. Obtener lotes FIFO: repository.findWithStockForFifo(request.productId())
        // 3. Iterar lotes restando currentStock
        // 4. Crear Sale y SaleItems
        // 5. Guardar todo en @Transactional
        // 6. Retornar SaleResponse
    }
}
```

## Testing Strategy

| Layer | What to Test | Approach |
|-------|-------------|----------|
| **Unit** | `SaleService.createSale()` FIFO logic | `SaleServiceTest.java` - Mockear `ProductionBatchRepository`, `SaleRepository`, `SaleItemRepository`. Probar: happy path, insufficient stock, FIFO order, race conditions (OptimisticLockException) |
| **Integration** | `SaleController` endpoints | `SaleControllerIT.java` - Usar `TestRestTemplate` + H2 (perfil test). Probar: 201 Created, 400 Bad Request (stock insuficiente), 403 Forbidden (OPERATOR), 401 Unauthorized (sin token) |
| **E2E** | N/A en esta fase | Futuro: tests end-to-end con Testcontainers + MySQL real |

### Unit Test Cases (SaleServiceTest)

1. ✅ `testCreateSale_Success_FIFOWorks()` - Happy path
2. ✅ `testCreateSale_InsufficientStock_ThrowsException()` - Stock insuficiente
3. ✅ `testCreateSale_FIFOOrder_CorrectDeduction()` - Verificar orden de lotes
4. ✅ `testCreateSale_OptimisticLockException_RetryOrFail()` - Race condition

### Integration Test Cases (SaleControllerIT)

1. ✅ `testCreateSale_Returns201Created()` - POST exitoso
2. ✅ `testCreateSale_Returns400_InsufficientStock()` - Stock insuficiente
3. ✅ `testCreateSale_Returns403_AsOperator()` - Usuario OPERATOR
4. ✅ `testCreateSale_Returns401_NoToken()` - Sin JWT

## Migration / Rollout

**No migration required.** Las tablas `sales` y `sale_items` YA EXISTEN en `V1__Initial_Schema.sql`. No crear nuevos archivos Flyway.

## Open Questions

- [ ] ¿La venta debe incluir información del usuario que la realizó? (No en MVP, futuro: agregar `user_id` a `sales` table)
- [ ] ¿Cómo manejar reintentos ante `OptimisticLockException`? (MVP: retornar error 409 Conflict, futuro: reintentar automáticamente)
- [ ] ¿Debería el sistema notificar stock bajo después de una venta? (Futuro: módulo Notifications)
