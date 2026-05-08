# Proposal: Sales Module (Módulo de Ventas)

## Intent

Implementar el módulo de **Ventas (Sales)** para permitir registrar salidas de productos terminados, aplicando la lógica **FIFO (First In, First Out)** para descontar stock de los lotes de producción más antiguos primero.

El sistema YA TIENE las tablas `sales` y `sale_items` en el esquema inicial (V1__Initial_Schema.sql). Esto es un **nuevo módulo completo** que sigue la arquitectura establecida.

## Scope

### In Scope
- Crear entidades JPA: `Sale.java` y `SaleItem.java`
- Crear repositorios Spring Data JPA: `SaleRepository`, `SaleItemRepository`
- Implementar `SaleService` con lógica de negocio:
  - Validar stock disponible antes de procesar
  - Obtener lotes FIFO usando `ProductionBatchRepository.findAllWithStockForFifo()`
  - Iterar lotes restando `currentStock`
  - Crear `Sale` y `SaleItem` en transacción `@Transactional`
- Crear `SaleController` con endpoint:
  - `POST /api/v1/sales` → Registrar venta (ACCESO: ADMIN)
- Crear DTOs: `SaleRequest`, `SaleResponse`, `SaleItemRequest`, `SaleItemResponse`
- Crear `SaleMapper` para conversión Entity ↔ DTO
- Documentación OpenAPI en controller
- Tests de integración: `SaleControllerIT`
- USAR `Strict TDD` (escribir tests ANTES que el código)

### Out of Scope
- GET endpoints para listar ventas (se hará en futura iteración)
- GET endpoint para obtener venta por ID (futuro)
- Reportes de ventas (futuro - módulo Reports)
- Módulo Inventory (stock final de productos envasados - futuro)
- Notificaciones de stock bajo (futuro - módulo Notifications)

## Capabilities

### New Capabilities
- `sales-module`: Módulo completo de ventas con lógica FIFO para registrar salidas de productos terminados

### Modified Capabilities
- None (no modifica capabilities existentes, solo lee de ProductionBatch)

## Approach

**Complete Standard Approach** — Seguir EXACTAMENTE el patrón de los módulos anteriores (Products, BulkProduct, ProductionBatch):

1. **Domain**: `Sale` (id, totalAmount, createdAt) y `SaleItem` (id, sale, productionBatch, quantity, unitPriceAtSale, unitCostAtSale)
2. **Repository**: `SaleRepository extends JpaRepository<Sale, Long>`, `SaleItemRepository extends JpaRepository<SaleItem, Long>`
3. **Service**: `SaleService` con método `createSale()` que implementa FIFO usando `ProductionBatchRepository.findAllWithStockForFifo()`
4. **Controller**: `SaleController` con `@PreAuthorize("hasRole('ADMIN')")` en endpoint POST
5. **DTOs**: Records inmutables (`SaleRequest`, `SaleResponse`, etc.) con validaciones Jakarta
6. **Mapper**: Manual mapper `SaleMapper` (aligual que otros módulos)
7. **Security**: Actualizar `SecurityConfig.java` para permitir `/api/v1/sales/**` solo a ADMIN

**FIFO Implementation**:
- El servicio recibe `productId` y `quantity` en el request
- Llama a `ProductionBatchRepository.findByProductIdAndCurrentStockGreaterThanOrderByProductionDateAsc(productId, 0)` (YA EXISTE)
- Itera lotes ordenados por fecha ascendente (más antiguo primero)
- Descuenta de cada lote hasta cubrir la cantidad vendida
- Usa `@Version` en ProductionBatch (YA CONFIGURADO) para prevenir race conditions

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `src/main/java/com/mundolimpio/application/sales/**` | New | Nuevo módulo completo (domain, repository, service, controller, dto, mapper) |
| `src/main/java/com/mundolimpio/application/security/config/SecurityConfig.java` | Modified | Agregar `/api/v1/sales/**` con acceso ADMIN |
| `src/test/java/com/mundolimpio/application/sales/**` | New | Tests de integración `SaleControllerIT` |
| `src/main/resources/db/migration/` | None | NO NECESITA NUEVA MIGRACIÓN (tablas ya existen en V1) |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| **FIFO Race Conditions** (múltiples ventas simultáneas) | Medium | ProductionBatch YA TIENE `@Version` para Optimistic Locking. Si hay conflicto → `OptimisticLockException`, se reintenta o maneja |
| **Stock Insufficient** (venta > stock disponible) | High | Validar stock total ANTES de procesar. Retornar `400 Bad Request` con mensaje claro: "Insufficient stock. Available: X, Requested: Y" |
| **Transactional Integrity** (falla al guardar Sale + Items + actualizar Batches) | Medium | Usar `@Transactional` en método de servicio. Si algo falla → rollback automático |
| **Security Misconfiguration** (ventas accesibles por OPERATOR) | Low | Usar `@PreAuthorize("hasRole('ADMIN')")` en controller. Actualizar SecurityConfig para restringir `/api/v1/sales/**` |

## Rollback Plan

Si algo sale mal después de merge a develop/main:

1. **Código**: `git revert <commit-hash>` para revertir el merge
2. **Base de datos**: Las tablas `sales` y `sale_items` ya existen desde V1, NO se puede hacer DROP (Flyway no soporta DROP easy). Si se quiere limpiar:
   - Opción A: Borrar datos manualmente: `DELETE FROM sale_items; DELETE FROM sales;`
   - Opción B: Dejar tablas vacías (no afecta funcionamiento)
3. **Configuración**: Remover `/api/v1/sales/**` de SecurityConfig si se revierte

## Dependencies

- **ProductionBatch Module**: El servicio de ventas LEE de `ProductionBatchRepository` (método `findAllWithStockForFifo()`). No modifica el módulo, solo lee.
- **SecurityConfig**: Necesita actualización para incluir nueva ruta.

## Success Criteria

- [ ] `Sale` y `SaleItem` entidades JPA creadas y mapeadas a tablas existentes
- [ ] `SaleService.createSale()` implementa FIFO correctamente (descuenta del lote más antiguo primero)
- [ ] Endpoint `POST /api/v1/sales` responde `201 Created` con estructura correcta
- [ ] Validación de stock insuficiente retorna `400 Bad Request` con mensaje claro
- [ ] Race conditions mitigadas por `@Version` (Optimistic Locking) en ProductionBatch
- [ ] Tests de integración `SaleControllerIT` PASAN (usando H2 en perfil test)
- [ ] Strict TDD: Tests escritos ANTES que la implementación
- [ ] OpenAPI documentation visible en `/swagger-ui.html`
- [ ] Security: Solo ADMIN puede registrar ventas
- [ ] NO SE CREARON NUEVAS MIGRACIONES FLYWAY (tablas ya existen)
