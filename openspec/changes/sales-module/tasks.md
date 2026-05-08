# Tasks: Sales Module (Módulo de Ventas)

## Phase 1: Domain & Repository (Foundation)

### Strict TDD: RED
- [ ] 1.1 Escribir test `testCreateSale_Success_FIFOWorks()` en `SaleServiceTest.java` (DEBE FALLAR - no hay clase SaleService aún)
- [ ] 1.2 Escribir test `testCreateSale_InsufficientStock_ThrowsException()` en `SaleServiceTest.java` (DEBE FALLAR)

### Phase 1: GREEN (Make it work)
- [x] 1.3 Crear `Sale.java` (JPA Entity: id, totalAmount, createdAt) en `sales/domain/`
- [x] 1.4 Crear `SaleItem.java` (JPA Entity: id, sale, productionBatch, quantity, unitPriceAtSale, unitCostAtSale) en `sales/domain/`
- [x] 1.5 Crear `SaleRepository.java` (`extends JpaRepository<Sale, Long>`) en `sales/repository/`
- [x] 1.6 Crear `SaleItemRepository.java` (`extends JpaRepository<SaleItem, Long>`) en `sales/repository/`
- [x] 1.7 Hacer pasar tests 1.1 y 1.2 (implementar lógica básica en `SaleService.java`)

### Phase 1: REFACTOR (Clean up)
- [x] 1.8 Ajustar anotaciones JPA: `@Entity`, `@Table`, `@Id`, `@GeneratedValue`, `@ManyToOne`, `@OneToMany`, `@Version` (si aplica)

## Phase 2: DTOs & Mapper

### Strict TDD: RED
- [ ] 2.1 Escribir test `testSaleMapper_ToEntity_ReturnsCorrectEntity()` en `SaleServiceTest.java` (DEBE FALLAR - no hay mapper)
- [ ] 2.2 Escribir test `testSaleMapper_ToResponse_ReturnsCorrectResponse()` en `SaleServiceTest.java` (DEBE FALLAR)

### Phase 2: GREEN
- [x] 2.3 Crear `SaleRequest.java` (record: productId, quantity + Jakarta Validation) en `sales/dto/`
- [x] 2.4 Crear `SaleResponse.java` (record: id, totalAmount, createdAt, items) en `sales/dto/`
- [x] 2.5 Crear `SaleItemResponse.java` (record: batchId, quantity, unitPrice, unitCost) en `sales/dto/`
- [x] 2.6 Crear `SaleMapper.java` (`@Component`, métodos `toEntity()`, `toResponse()`) en `sales/mapper/`
- [x] 2.7 Hacer pasar tests 2.1 y 2.2

### Phase 2: REFACTOR
- [x] 2.8 Verificar que los records usen inmutabilidad (Java Records)
- [x] 2.9 Asegurar que las validaciones Jakarta estén completas (`@NotNull`, `@Positive`)

## Phase 3: Service Implementation (FIFO Logic)

### Strict TDD: RED
- [ ] 3.1 Escribir test `testCreateSale_FIFOOrder_CorrectDeduction()` en `SaleServiceTest.java` (DEBE FALLAR - método no implementado)
- [ ] 3.2 Escribir test `testCreateSale_OptimisticLockException_RetryOrFail()` en `SaleServiceTest.java` (DEBE FALLAR)

### Phase 3: GREEN
- [ ] 3.3 Implementar `SaleService.java` con método `createSale(SaleRequest)`:
  - Validar stock total disponible ANTES de procesar
  - Llamar `ProductionBatchRepository.findWithStockForFifo(productId)` (YA EXISTE)
  - Iterar lotes FIFO restando `currentStock` (usar `BigDecimal.subtract()`)
  - Crear `Sale` y `SaleItem` (en `@Transactional`)
  - Actualizar `ProductionBatch` repository
- [ ] 3.4 Inyectar dependencias: `SaleRepository`, `SaleItemRepository`, `ProductionBatchRepository`, `SaleMapper`
- [ ] 3.5 Hacer pasar tests 3.1 y 3.2

### Phase 3: REFACTOR
- [ ] 3.6 Extraer lógica de cálculo de `totalAmount` a método privado
- [ ] 3.7 Manejar `OptimisticLockException` (retornar `409 Conflict` o reintentar)
- [ ] 3.8 Agregar logging con SLF4J para auditoría

## Phase 4: Controller & Security

### Strict TDD: RED
- [ ] 4.1 Escribir test `testCreateSale_Returns201Created()` en `SaleControllerIT.java` (DEBE FALLAR - no hay controller)
- [ ] 4.2 Escribir test `testCreateSale_Returns400_InsufficientStock()` en `SaleControllerIT.java` (DEBE FALLAR)
- [ ] 4.3 Escribir test `testCreateSale_Returns403_AsOperator()` en `SaleControllerIT.java` (DEBE FALLAR)
- [ ] 4.4 Escribir test `testCreateSale_Returns401_NoToken()` en `SaleControllerIT.java` (DEBE FALLAR)

### Phase 4: GREEN
- [ ] 4.5 Crear `SaleController.java` (`@RestController`, `@RequestMapping("/api/v1/sales")`)
  - `@PreAuthorize("hasRole('ADMIN')")` en método POST
  - `@Operation` y `@ApiResponse` para OpenAPI
  - Llamar a `SaleService.createSale()`
  - Retornar `ResponseEntity<SaleResponse>` con HttpStatus.CREATED
- [ ] 4.6 Actualizar `SecurityConfig.java`: agregar `.requestMatchers("/api/v1/sales/**").hasRole("ADMIN")`
- [ ] 4.7 Hacer pasar tests 4.1, 4.2, 4.3, 4.4

### Phase 4: REFACTOR
- [ ] 4.8 Verificar que los mensajes de error en 400/403/401 sean claros y consistentes
- [ ] 4.9 Revisar que OpenAPI esté completo (descripciones, response codes)

## Phase 5: Integration Tests (Strict TDD - Integration Layer)

### Strict TDD: RED (Tests ya creados en 4.1-4.4, ahora usar H2)
- [ ] 5.1 Completar `SaleControllerIT.java`:
  - Configurar `TestRestTemplate` con H2 (perfil test)
  - Pre-cargar datos: Product, BulkProduct, ProductionBatch con stock
  - Probar flujo completo: POST → 201 → GET (si existe) → verificar FIFO
- [ ] 5.2 Agregar test `testCreateSale_ConcurrentSale_HandlesRaceCondition()` en `SaleControllerIT.java` (simular con `@DirtiesContext`)

### Phase 5: GREEN
- [ ] 5.3 Ejecutar TODOS los tests: `./mvnw test` → DEBEN PASAR TODOS
- [ ] 5.4 Verificar cobertura: `./mvnw test jacoco:report` (objetivo: >80% en sales/*)

### Phase 5: REFACTOR
- [ ] 5.5 Limpiar código duplicado en tests
- [ ] 5.6 Renombrar variables si no son descriptivas
- [ ] 5.7 Eliminar imports no usados

## Phase 6: Documentation & Final Verification

- [ ] 6.1 Actualizar `PROJECT_HISTORY.md`:
  - Agregar sección "Fase 5: Módulo de Ventas"
  - Documentar decisiones de diseño (reuso de findAllWithStockForFifo)
  - Listar archivos creados
- [ ] 6.2 Actualizar `README.md`:
  - Agregar sección "Ventas (`/api/v1/sales`)"
  - Documentar request/response format
  - Agregar ejemplos de uso con curl/JSON
- [ ] 6.3 Ejecutar `./mvnw clean verify` (build completo + tests + integration)
- [ ] 6.4 Hacer commit con Conventional Commits: `feat(sales): implement complete sales module with FIFO logic`
- [ ] 6.5 Crear branch `feature/sales` y push al remoto
- [ ] 6.6 Crear PR a `develop` siguiendo workflow del proyecto
