# Mundo Limpio Backend - Historia del Proyecto

Documento vivo que describe la evolución, arquitectura y estado actual del proyecto.

---

## Índice
1. [Evolución del Proyecto](#evolución-del-proyecto)
2. [Arquitectura](#arquitectura)
3. [Patrones de Diseño](#patrones-de-diseño)
4. [Estado Actual](#estado-actual)
5. [Próximos Pasos](#próximos-pasos)
6. [Workflow de Desarrollo](#workflow-de-desarrollo)

---

## Evolución del Proyecto

### Fase 1: Configuración Inicial (Marzo 2026)

#### 2026-03-03 - c6875ee: Primer commit
- Setup inicial del proyecto Spring Boot
- Estructura básica de Maven
- Aplicación vacía con test de arranque

**Archivos creados:**
- Estructura Maven básica (pom.xml)
- MundoLimpioApplication.java
- MundoLimpioApplicationTests.java
- Configuración de Maven Wrapper

---

#### 2026-03-04 - 3836f05: Initial project setup with Spring Boot and Docker
- Configuración completa de Spring Boot 3.3.0
- Integración con MySQL y JPA
- Configuración de Docker Compose para MySQL
- Cambio de application.properties a application.yml

**Cambios:**
- docker-compose.yml para levantar MySQL
- pom.xml con dependencias: spring-boot-starter-web, spring-boot-starter-data-jpa, mysql-connector-j
- .dockerignore configurado

---

#### 2026-03-04 - 87c893f: Configure Flyway and initial database schema
- Integración de Flyway para migraciones de base de datos
- Schema inicial de la base de datos

**Archivos creados:**
- `src/main/resources/db/migration/V1__Initial_Schema.sql`
  - Tabla `products` (id, sku, name, min_price, active)
  - Tabla `bulk_products` (id, name, stock, cost, conversion_ratio)
  - Tabla `production_batches` (id, product_id, bulk_product_id, initial_quantity, current_stock, unit_cost_at_production, raw_quantity_used, production_date)
  - Tabla `users` (id, username, password, role)
  - Relaciones FK entre production_batches y products/bulk_products

**Decisión técnica:** Usar Flyway en lugar de `spring.jpa.hibernate.ddl-auto=update` para tener control versionado de los cambios en la base de datos. Esto permite rollback controlado y migraciones reproducibles.

---

#### 2026-03-04 - ddde625: Update CI
- Configuración de GitHub Actions para CI

**Archivos creados:**
- `.github/workflows/ci.yml`
  - Build con Maven
  - Ejecución de tests

---

#### 2026-03-05 - 299fbba: Configure test profile using H2 database
- Configuración de perfil de test con base de datos H2 in-memory
- Permite ejecutar tests sin depender de MySQL externo

**Cambios:**
- pom.xml: agregada dependencia H2 (scope test)
- `src/test/resources/application-test.yml`: configuración H2
- Actualización de MundoLimpioApplicationTests para usar perfil test

**Decisión técnica:** El uso de H2 para tests permite ejecutar la suite de tests en cualquier entorno sin configuración externa de base de datos. Flyway corre las migraciones sobre H2 en tests, asegurando que el schema sea idéntico al de producción.

---

### Fase 2: Módulo de Productos (Marzo-Mayo 2026)

#### Branch: `feature/product-module`

**Commits principales:**
- `65b35e0`: Implementación inicial - create y getBySku
- `3440042`: CRUD completo con validación, error handling y OpenAPI
- `f56b38b`: Actualización de ProductControllerIT
- `69223cc`: Módulo completo con tests

**Lo que se implementó:**
1. **Domain** (`Product.java`)
   - Entidad JPA con id, sku, name, minPrice, active
   - SKU único como identificador de negocio
   - Soft delete (campo active)

2. **Repository** (`ProductRepository.java`)
   - Spring Data JPA
   - Métodos: findBySku, findByActiveTrue

3. **DTOs** (`ProductRequest`, `ProductResponse`)
   - Request: sku, name, minPrice
   - Response: id, sku, name, minPrice, active

4. **Mapper** (`ProductMapper.java`)
   - Conversión entre Entity ↔ DTO
   - Uso de Lombok para reducir boilerplate

5. **Service** (`ProductService.java`)
   - Lógica de negocio
   - Validaciones: SKU único, precio válido
   - Soft delete (no elimina, marca active=false)
   - Reactivar producto (PATCH)

6. **Controller** (`ProductController.java`)
   - Endpoints REST completos (ver README.md)
   - Documentación OpenAPI/Swagger
   - Validación con Jakarta Validation

7. **Exception Handling**
   - `ProductNotFoundException`
   - `ProductAlreadyExistsException`
   - `GlobalExceptionHandler` para respuestas 404, 409, 400

8. **Tests**
   - `ProductControllerIT`: Integración con TestRestTemplate
   - Usa H2 en memoria
   - Tests de happy path y error cases

**Decisión de diseño - Soft Delete:**
Se optó por soft delete (marcar active=false) en lugar de hard delete para:
- Mantener integridad referencial con production_batches
- Preservar historial de ventas
- Permitir reactivación si se eliminó por error

---

### Fase 3: Módulo de Materia Prima (Abril-Mayo 2026)

#### Branch: `feature/bulk-products`

**Lo que se implementó:**
1. **Domain** (`BulkProduct.java`)
   - Entidad con id, name, stock, cost, conversionRatio
   - conversionRatio: cuánto producto terminado sale de 1 unidad de materia prima

2. **Repository** (`BulkProductRepository`)
   - Spring Data JPA

3. **DTOs** (`BulkProductRequest`, `BulkProductResponse`)

4. **Service** (`BulkProductService`)
   - CRUD completo
   - Solo accesible por ADMIN

5. **Controller** (`BulkProductController`)
   - Endpoints protegidos con `@PreAuthorize("hasRole('ADMIN')")`

6. **Tests**
   - `BulkProductControllerIT`

**Decisión de diseño - Conversion Ratio:**
El campo `conversionRatio` permite calcular cuánto producto terminado se produce a partir de materia prima. Ejemplo: 1kg de químico → 10 botellas de limpiador (ratio=10).

---

### Fase 4: Lotes de Producción (Mayo 2026)

#### Branch: `feature/production-batches` (actual)

**Lo que se implementó:**
1. **Domain** (`ProductionBatch.java`)
   - Lote de producción vinculado a Product y BulkProduct
   - initialQuantity: cantidad producida
   - currentStock: stock actual (para FIFO)
   - unitCostAtProduction: costo unitario al momento de producir
   - rawQuantityUsed: materia prima consumida
   - `@Version` para optimistic locking

2. **Service** (`ProductionBatchService`)
   - Crear lote: descuenta stock de BulkProduct, calcula costos
   - Obtener lotes por producto (para aplicar FIFO en ventas)

3. **Controller** (`ProductionBatchController`)
   - Crear lote (POST)
   - Obtener por ID
   - Listar por producto

4. **Tests**
   - `ProductionBatchControllerIT`
   - `ProductionBatchServiceTest` (unitario)

**Decisión de diseño - FIFO:**
Los lotes mantienen `currentStock` para implementar FIFO (First In, First Out) en las ventas. Al vender, se descuenta primero del lote más antiguo.

**Decisión de diseño - Optimistic Locking:**
El campo `@Version` en ProductionBatch previene condiciones de carrera al producir o vender.

### Fase 5: Módulo de Ventas (Mayo 2026)

#### Branch: `feature/sales`

**Lo que se implementó:**

1. **Domain** (`Sale.java`, `SaleItem.java`)
   - Entidad Sale con id, totalAmount, createdAt, @Version
   - Entidad SaleItem con snapshot del lote (productionBatchId, quantity, unitPriceAtSale, unitCostAtSale)
   - Relación bidireccional con cascade = ALL, orphanRemoval = true
   - Soft delete no aplica en ventas (registro permanente de auditoría)

2. **Repository** (`SaleRepository`, `SaleItemRepository`)
   - Spring Data JPA con JpaRepository
   - Queries derivadas para futuras búsquedas

3. **DTOs** (`SaleRequest`, `SaleResponse`, `SaleItemResponse`)
   - SaleRequest: productId + quantity (BigDecimal para fracciones)
   - Validaciones Jakarta: @NotNull, @Positive
   - Java Records para inmutabilidad

4. **Mapper** (`SaleMapper`)
   - Conversión Entity → DTO (toResponse)
   - Conversión manual (sin MapStruct)

5. **Service** (`SaleService`)
   - **Lógica FIFO completa**: descuenta stock del lote más antiguo primero
   - Si un lote no alcanza, complementa con el siguiente
   - Validación de stock antes de procesar
   - @Transactional: atomicidad (todo o nada)
   - Manejo de OptimisticLockingFailureException

6. **Controller** (`SaleController`)
   - POST `/api/v1/sales` (ADMIN only)
   - @PreAuthorize("hasRole('ADMIN')")
   - Documentación OpenAPI completa
   - Retorna 201 Created con SaleResponse

7. **Security & Exception Handling** (fixes del sistema)
   - **BUG CRÍTICO FIX**: JwtAuthenticationFilter tenía `username == null` en vez de `!= null` — la autenticación JWT no funcionaba
   - HttpStatusEntryPoint(UNAUTHORIZED) en SecurityConfig para respuestas REST correctas
   - GlobalExceptionHandler: IllegalArgumentException (400), AccessDeniedException (403), OptimisticLockingFailureException (409)

8. **Tests** (`SaleControllerIT` — 8 tests)
   - 401: Sin token → rechazado
   - 403: Operator → rechazado
   - 201: Venta exitosa con stock
   - 400: Stock insuficiente
   - FIFO con múltiples lotes (oldest first)
   - Deducción parcial (stock restante verificado)
   - Venta fallida no modifica stock (atomicidad)
   - Optimistic locking version detection

**Decisión de diseño - Costo = Precio de venta:**
Actualmente el precio de venta usa el costo unitario del lote (`getUnitCostAtProduction()`). Esto es temporal — en el futuro se necesita un campo de precio de venta separado en el producto para calcular márgenes de ganancia reales.

**Decisión de diseño - Snapshot de costo:**
Cada SaleItem guarda `unitCostAtSale` como snapshot del costo en el momento de la venta. Si el costo del lote cambia después, el registro de venta no se altera. Esto es esencial para contabilidad y auditoría.

**Decisión de diseño - Optimistic Locking en Sales:**
El @Version en ProductionBatch protege contra condiciones de carrera. Si dos ventas concurrentes intentan descontar del mismo lote, la segunda recibe 409 Conflict.

---

### Fase 6: CORS Configuration (Mayo 2026)

#### Branch: `feature/cors` (o integrado en `develop`)

**Lo que se implementó:**
1. **CorsConfig.java** (NUEVO)
   - `@Configuration` con bean `CorsConfigurationSource`
   - Usa `UrlBasedCorsConfigurationSource` registrando `/**` con configuración CORS completa
   - Orígenes externalizados via `@Value("${application.cors.allowed-origins}")`
   - Config: allowedOrigins (localhost:8080, 10.0.2.2:8080), allowedMethods (GET/POST/PUT/PATCH/DELETE/OPTIONS), allowedHeaders (Authorization/Content-Type/Accept/Origin), exposedHeaders (Authorization), allowCredentials(false), maxAge(3600)
   - Comentarios en español explicando cada decisión técnica

2. **SecurityConfig.java** (MODIFICADO)
   - `.cors(cors -> cors.configurationSource(corsConfigurationSource))` agregado ANTES de `.csrf()`
   - `.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()` para que preflight bypassee auth
   - Comentarios en español explicando por qué CORS va antes que auth

3. **application.yml** (MODIFICADO)
   - `application.cors.allowed-origins: http://localhost:8080, http://10.0.2.2:8080`

4. **Tests**
   - `CorsConfigTest`: Verifica que el bean CorsConfigurationSource existe con orígenes, métodos, headers, maxAge y allowCredentials correctos
   - `CorsSecurityTest` (3 tests): Verifica que preflight OPTIONS retorna 200 con headers CORS, que origen no permitido no recibe headers, y que GET con Origin aún requiere auth

**Decisión de diseño - CorsConfigurationSource vs WebMvcConfigurer:**
Se usó `@Bean CorsConfigurationSource` en vez de `WebMvcConfigurer` porque:
- CorsConfigurationSource corre a nivel de FILTRO de Spring Security, antes de autenticación
- WebMvcConfigurer corre a nivel de INTERCEPTOR de Spring MVC, después de autenticación
- Para que el preflight OPTIONS funcione sin auth, necesitamos el approach de filtro

**Decisión de diseño - allowCredentials(false):**
La autenticación usa JWT Bearer token (en header Authorization), no cookies. Con `allowCredentials=false` podemos usar multi-value allowedOrigins sin restricciones.

**Decisión de diseño - maxAge(3600):**
El browser cachea la respuesta del preflight OPTIONS por 1 hora, reduciendo latencia en requests consecutivos de la app Flutter.

**Orígenes configurados:**
- `http://localhost:8080` — iOS simulator y desarrollo local
- `http://10.0.2.2:8080` — Android emulator (10.0.2.2 es localhost del host)

**Descubrimiento crítico:**
- `CorsFilter` de Spring Security (vía `.cors()`) NO short-circuita el filter chain para preflights válidos — agrega headers CORS pero continúa la cadena. Por eso es necesario también permitir OPTIONS en `authorizeHttpRequests`.
- `TestRestTemplate` (vía `HttpURLConnection`) no expone correctamente headers `Access-Control-*` en las respuestas. Para tests de CORS, usar `MockMvc`.

---

### Fase 7: Auth Refresh Token (Mayo 2026)

#### Branch: `feature/auth-refresh-token`

**Objetivo:** Implementar endpoint POST `/api/v1/auth/refresh` que permita renovar el access token JWT usando el refresh token (7 días) que ya se genera en login/register.

**SDD Phases completadas:**
- Explore → Propose → Spec (6 reqs, 12 scenarios) → Design → Tasks (12 tasks en 4 fases)

**Phase 1: Foundation (este commit)**
Archivos creados:

1. **`user/dto/RefreshRequest.java`** (NUEVO)
   - Record con `@NotBlank String refreshToken`
   - Jakarta Validation para rechazar tokens vacíos antes de llegar al service

2. **`user/exception/InvalidRefreshTokenException.java`** (NUEVO)
   - Extiende `RuntimeException`
   - Enum anidado `RefreshError`: EXPIRED, INVALID, USER_NOT_FOUND, MALFORMED
   - Mensajes descriptivos en español para cada causa

3. **`user/exception/AuthExceptionHandler.java`** (NUEVO)
   - `@ControllerAdvice(basePackages = "com.mundolimpio.application.user")`
   - `@Order(Ordered.HIGHEST_PRECEDENCE)` — se ejecuta ANTES que GlobalExceptionHandler
   - Captura `InvalidRefreshTokenException` → HTTP 401 con `ErrorResponse`
   - Fundamental: sin `@Order`, el catch-all de `GlobalExceptionHandler` devolvería 500

**Decisión de diseño - `@Order` en Exception Handlers:**
El `GlobalExceptionHandler` existente no tiene `@Order`, por lo que Spring le asigna la prioridad más baja (`LOWEST_PRECEDENCE`). El nuevo `AuthExceptionHandler` usa `@Order(Ordered.HIGHEST_PRECEDENCE)` para garantizar que Spring lo evalúe PRIMERO cuando la excepción ocurre en el módulo `user`. Si no, `InvalidRefreshTokenException` (que extiende `RuntimeException` → `Exception`) caería en el catch-all de `GlobalExceptionHandler` y devolvería 500.

**Decisión de diseño - RefreshError enum anidado:**
Se optó por un enum anidado dentro de `InvalidRefreshTokenException` (en vez de archivo separado) porque:
- Cohesión semántica: la razón del error no tiene sentido fuera del contexto de la excepción
- Menos archivos = menos complejidad
- El enum solo se usa en el handler y el service, no se expone en la API

**Phase 2: Core Implementation**
Archivos modificados:

1. **`AuthService.java`** (MODIFICADO)
   - Agregado `CustomUserDetailsService` como dependencia (constructor injection)
   - Agregado `@Value("${application.security.jwt.refresh-expiration}")` con default `:604800000`
   - Reemplazado `604800000L` hardcodeado por `refreshExpiration` en `register()` y `login()`
   - Nuevo método `refresh(RefreshRequest)`: extrae username → carga usuario via `CustomUserDetailsService` → valida token → genera nuevo par → retorna `LoginResponse`
   - Error handling: `JwtException` → MALFORMED, `UsernameNotFoundException` → USER_NOT_FOUND, `!isTokenValid` → INVALID

2. **`AuthController.java`** (MODIFICADO)
   - Nuevo endpoint `POST /api/v1/auth/refresh` con Swagger docs
   - Acepta `@Valid @RequestBody RefreshRequest`, delega a `authService.refresh()`
   - Retorna 200 OK o 401 via AuthExceptionHandler

**Phase 3: Testing**
Archivos creados:

1. **`AuthServiceTest.java`** (4 tests, Mockito)
   - `validToken_ShouldReturnNewTokens`: token válido → LoginResponse con nuevos tokens
   - `malformedToken_ShouldThrowMalformed`: token malformado → InvalidRefreshTokenException(MALFORMED)
   - `deletedUser_ShouldThrowUserNotFound`: usuario eliminado → InvalidRefreshTokenException(USER_NOT_FOUND)
   - `expiredToken_ShouldThrowInvalid`: token expirado → InvalidRefreshTokenException(INVALID)

2. **`AuthControllerTest.java`** (2 tests, `@SpringBootTest` + `@MockBean`)
   - `shouldReturn200WhenRefreshSucceeds`: POST /refresh con token válido → 200 OK
   - `shouldReturn401WhenRefreshFails`: POST /refresh con token inválido → 401 UNAUTHORIZED

3. **`AuthRefreshIT.java`** (1 test, `@SpringBootTest(RANDOM_PORT)` + TestRestTemplate)
   - `shouldRefreshTokenSuccessfully`: register → login → POST /refresh → 200 + tokens válidos

**Phase 4: Wiring**
- Uncommented `refresh-expiration: 604800000` en `application.yml`
- `mvn verify` → 20 tests, 0 failures ✅

**Decisión de diseño - @Value default:**
Se agregó `:604800000` como default en `@Value("${application.security.jwt.refresh-expiration:604800000}")` para que los tests pasaran ANTES de descomentar la propiedad en `application.yml`. En Phase 4 se descomentó, y Spring usa el valor del YAML en vez del default.

**Decisión de diseño - AuthControllerTest con @SpringBootTest:**
Se usó `@SpringBootTest` en vez de `@WebMvcTest` porque `@WebMvcTest` no carga los filters de seguridad correctamente (JwtAuthenticationFilter necesita JwtService). Como `/api/v1/auth/**` es `permitAll()`, el contexto completo permite testear el endpoint sin autenticación.

**Total tests nuevos: 7** (AuthServiceTest: 4, AuthControllerTest: 2, AuthRefreshIT: 1)

---

### Fase 8: Módulo de Inventario (Mayo 2026)

#### Branch: `feature/inventory-module`

**PR #3 (Integration):**
- Wire InventoryService into ProductionBatchService (increment stock on batch creation)
- Wire InventoryService into SaleService (decrement stock on sale)
- Updated README.md and PROJECT_HISTORY.md

**Lo que se implementó:**

1. **Domain** (`Inventory.java`, `InventoryAdjustment.java`)
   - `Inventory`: entidad 1:1 con Product (product_id FK + UNIQUE), current_stock, min_stock_threshold, @Version
   - `InventoryAdjustment`: trail de auditoría con type, quantity, reason
   - Relación separada de Product para no contaminar la entidad de catálogo con datos operacionales
   - `@Version` en Inventory para optimistic locking en ajustes concurrentes

2. **Repository** (`InventoryRepository.java`, `InventoryAdjustmentRepository.java`)
   - `findByProductId(Long)`: Optional<Inventory> para consulta por producto
   - `findLowStockInventories()`: @Query JPQL para productos donde currentStock < minStockThreshold

3. **DTOs** (`InventoryResponse.java`, `AdjustmentRequest.java`)
   - `InventoryResponse`: productId, productName, currentStock, minStockThreshold
   - `AdjustmentRequest`: type, quantity (con signo: +aumenta, -disminuye), reason
   - Jakarta Validation en AdjustmentRequest: @NotBlank type/reason, @NotNull quantity

4. **Mapper** (`InventoryMapper.java`)
   - `toResponse(Inventory)`: mapea entidad a DTO con product.getActive() como nombre producto
   - Conversión manual (sin MapStruct) consistente con el resto del proyecto

5. **Service** (`InventoryService.java`)
   - `getInventory(Long productId)`: consulta stock de un producto (404 si no existe)
   - `getLowStockInventories()`: lista productos con stock bajo el umbral mínimo
   - `adjustStock(Long productId, AdjustmentRequest)`: ajuste manual con validación de stock no negativo, actualización de Inventory y creación de audit trail (InventoryAdjustment) en la misma transacción @Transactional
   - `incrementStock(Long productId, BigDecimal quantity)`: público (para integración entre módulos), find-or-create pattern, sin auditoría (la auditoría es el evento de negocio mismo)
   - `decrementStock(Long productId, BigDecimal quantity)`: público (para integración entre módulos), valida stock suficiente, sin auditoría

6. **Controller** (`InventoryController.java`)
   - GET `/api/v1/inventory/{productId}`: obtener stock por producto
   - GET `/api/v1/inventory/low-stock`: productos con stock bajo
   - POST `/api/v1/inventory/{productId}/adjust`: ajuste manual con auditoría
   - Todos los endpoints requieren ROLE_ADMIN
   - Documentación OpenAPI/Swagger completa

7. **Exception Handling**
   - `InventoryNotFoundException` → 404
   - `InvalidAdjustmentException` → 400
   - `GlobalExceptionHandler` captura ambas (ya existía para otros módulos)

8. **Tests**
   - `InventoryControllerTest` (7 tests, @SpringBootTest + MockMvc): 200, 401, 400, validación
   - `InventoryServiceTest` (10 tests, Mockito): getInventory, lowStock, adjustStock, incrementStock, decrementStock, concurrencia
   - `ProductionBatchServiceTest` actualizado: 4 tests (incluye integración con Inventory)
   - `SaleServiceTest` actualizado: 5 tests (incluye integración con Inventory)

9. **Integración entre módulos** (PR #3)
   - `ProductionBatchService.createBatch()`: llama a `inventoryService.incrementStock(productId, initialQuantity)` después de guardar el lote
   - `SaleService.createSale()`: llama a `inventoryService.decrementStock(productId, quantity)` después de guardar la venta
   - Ambas llamadas ocurren DENTRO del mismo @Transactional para mantener consistencia transaccional
   - `InventoryService.incrementStock()` y `decrementStock()` son públicos (package-private originalmente, cambiado a public para integración cross-package)

**Decisión de diseño — Separación de Inventory vs ProductionBatch:**
- `production_batches` trackea stock por LOTE (para FIFO). Cada lote tiene su propio current_stock.
- `inventory` trackea stock TOTAL del producto (1:1 con Product). Un solo valor por producto.
- Ambos se actualizan en la misma transacción para mantener consistencia.
- Diferencia clave: FIFO descuenta de lotes individuales; Inventory refleja el saldo total.

**Decisión de diseño — incrementStock/decrementStock públicos:**
- Originalmente diseñados como package-private para restringir acceso.
- Se cambiaron a public porque ProductionBatchService (productionbatch.service) y SaleService (sales.service) están en paquetes diferentes.
- Alternativa considerada: mover la lógica a un servicio común. Se descartó porque aumentaba la complejidad sin beneficio claro.
- Los métodos siguen SIN exponerse como endpoints REST (solo adjustStock es endpoint).

**Decisión de diseño — Sin auditoría en increment/decrement:**
- increment/decrement NO crean InventoryAdjustment. La auditoría de estas operaciones está en los eventos de negocio mismos (ProductionBatch para incrementos, Sale para decrementos).
- adjustStock SÍ crea InventoryAdjustment porque es una acción manual del ADMIN que necesita justificación.

**Decisión de diseño — @Version en Inventory:**
- Protege contra race conditions en ajustes manuales concurrentes (dos ADMINs ajustando el mismo producto).
- Consistente con el patrón usado en ProductionBatch y Sale.
- En integraciones (increment/decrement), el @Version del Inventory puede lanzar OptimisticLockingFailureException si hay concurrencia, pero el @Transactional maneja el rollback.

---

### Fase 9: Gestión de Usuarios — Users Management (Mayo 2026)

#### Branch: `feature/users-management`

**3 stacked PRs: Foundation → Core → Polish**

**PR #1 — Foundation (Service + DTOs + Exceptions):**
Archivos creados:

1. **`user/service/UserManagementService.java`** (NUEVO)
   - Servicio separado de AuthService (SRP)
   - findAll, findById, changeRole (valida rol, self-demotion), resetPassword (BCrypt)
   - @Transactional en métodos de escritura

2. **`user/dto/UserResponse.java`** (NUEVO)
   - Record: id, username, role, createdAt — sin password

3. **`user/dto/ChangeRoleRequest.java`** (NUEVO)
   - Record: @NotBlank String newRole

4. **`user/dto/ResetPasswordRequest.java`** (NUEVO)
   - Record: @NotBlank @Size(min=6) String newPassword

5. **`user/mapper/UserMapper.java`** (NUEVO)
   - @Component, User → UserResponse (sigue patrón InventoryMapper)

6. **`user/exception/UserNotFoundException.java`** (NUEVO)
   - RuntimeException con Long id, mensaje "User not found with ID: {id}"

7. **`user/exception/UserExceptionHandler.java`** (NUEVO)
   - @ControllerAdvice(basePackages = "com.mundolimpio.application.user")
   - @Order(Ordered.HIGHEST_PRECEDENCE) — antes que GlobalExceptionHandler
   - Captura UserNotFoundException → 404 USER_NOT_FOUND
   - Captura IllegalArgumentException → 400 con código del mensaje (INVALID_ROLE, SELF_DEMOTION, INVALID_PASSWORD)
   - Sigue el patrón exacto de AuthExceptionHandler

8. **`test/.../user/service/UserManagementServiceTest.java`** (NUEVO)
   - Mockito: 10 tests (findAll, findById, changeRole 4 casos, resetPassword, etc.)

**PR #2 — Controller (Core):**
Archivos creados/modificados:

1. **`user/controller/UserManagementController.java`** (NUEVO)
   - 4 endpoints ADMIN-only con @PreAuthorize("hasRole('ADMIN')")
   - GET /api/v1/users, GET /api/v1/users/{id}, PATCH /api/v1/users/{id}/role, PATCH /api/v1/users/{id}/password
   - Self-demotion guard via SecurityContextHolder.getContext().getAuthentication()
   - Swagger/OpenAPI annotations (@Tag, @Operation, @ApiResponses)
   - Sigue patrón InventoryController

2. **`common/handler/GlobalExceptionHandler.java`** (MODIFICADO)
   - Agregado @ExceptionHandler(UserNotFoundException.class) → 404 USER_NOT_FOUND (fallback)

3. **`test/.../user/controller/UserManagementControllerTest.java`** (NUEVO)
   - @SpringBootTest + MockMvc: 12 tests (200, 401, 403, 400, 404 en todos los endpoints)

**PR #3 — Polish (Integration test + Docs):**
Archivos creados/modificados:

1. **`test/.../user/controller/UserManagementIT.java`** (NUEVO)
   - @SpringBootTest(RANDOM_PORT) + TestRestTemplate: 11 tests de integración
   - Crea usuario ADMIN en DB, genera JWT real via JwtService
   - Prueba lista completa de usuarios con 2 registros y solo admin
   - Prueba detalle de usuario (existente e inexistente → 404)
   - Prueba cambio de rol (éxito, rol inválido, autodemoción, usuario inexistente)
   - Prueba reset de password (verifica login con nueva contraseña)
   - Verifica que OPERATOR recibe 403 en endpoint ADMIN
   - Requiere httpclient5 (test scope) para soporte HTTP PATCH en TestRestTemplate

2. **`pom.xml`** (MODIFICADO)
   - Agregada dependencia httpclient5 (test scope) para soporte PATCH en TestRestTemplate

3. **README.md** y **PROJECT_HISTORY.md** actualizados

**Decisiones de diseño — Users Management:**

**Separación de AuthService (SRP):**
- AuthService maneja register, login, refresh (flujos públicos/de autenticación)
- UserManagementService maneja findAll, findById, changeRole, resetPassword (operaciones ADMIN)
- Si se mezclaran, el controlador tendría endpoints públicos y ADMIN-only mezclados

**Self-demotion guard en controller, no en service:**
- Controller extrae currentUserId del SecurityContextHolder y lo pasa al service
- El service no depende de SecurityContext (acoplamiento)
- El service recibe el ID como parámetro y se mantiene testeable con Mockito

**Validación en cascada:**
- Jakarta Validation (@NotBlank, @Size) en DTOs: errores estructurales → VALIDATION_ERROR
- Service valida reglas de negocio: rol válido, autodemoción → códigos específicos (INVALID_ROLE, SELF_DEMOTION)
- UserExceptionHandler captura ambas capas con @Order(HIGHEST_PRECEDENCE)

**PATCH requiere Apache HttpClient5:**
- HttpURLConnection no soporta PATCH
- TestRestTemplate usa SimpleClientHttpRequestFactory por defecto
- Se agregó httpclient5 (test scope) y se configuró HttpComponentsClientHttpRequestFactory en @BeforeEach
- Alternativa considerada: reflection para forzar PATCH en HttpURLConnection, descartada por ser frágil

**Tests totales nuevos: 33** (UserManagementServiceTest: 10, UserManagementControllerTest: 12, UserManagementIT: 11)
**Coverage antes: 61 tests → después: 73 tests (sin IT) o 84 (con IT)**

---

## Arquitectura

### Estructura de Capas

```
src/main/java/com/mundolimpio/application/
├── MundoLimpioApplication.java          # Entry point
│
├── product/                             # Módulo Productos
│   ├── domain/Product.java             # Entidad JPA
│   ├── repository/ProductRepository.java
│   ├── service/ProductService.java     # Lógica de negocio
│   ├── controller/ProductController.java
│   ├── dto/ProductRequest.java
│   ├── dto/ProductResponse.java
│   └── mapper/ProductMapper.java
│
├── bulkproduct/                         # Módulo Materia Prima
│   ├── domain/BulkProduct.java
│   ├── repository/BulkProductRepository.java
│   ├── service/BulkProductService.java
│   ├── controller/BulkProductController.java
│   ├── dto/BulkProductRequest.java
│   ├── dto/BulkProductResponse.java
│   └── mapper/BulkProductMapper.java
│
├── productionbatch/                      # Módulo Lotes
│   ├── domain/ProductionBatch.java
│   ├── repository/ProductionBatchRepository.java
│   ├── service/ProductionBatchService.java
│   ├── controller/ProductionBatchController.java
│   ├── dto/ProductionBatchRequest.java
│   ├── dto/ProductionBatchResponse.java
│   └── mapper/ProductionBatchMapper.java
│
├── sales/                               # Módulo Ventas (FIFO)
│   ├── domain/Sale.java                # Entidad con @Version
│   ├── domain/SaleItem.java            # Item con snapshot de costo
│   ├── repository/SaleRepository.java
│   ├── repository/SaleItemRepository.java
│   ├── service/SaleService.java        # Lógica FIFO + @Transactional
│   ├── controller/SaleController.java   # POST /api/v1/sales (ADMIN)
│   ├── dto/SaleRequest.java            # productId, quantity
│   ├── dto/SaleResponse.java           # id, totalAmount, items
│   ├── dto/SaleItemResponse.java       # batchId, quantity, costs
│   └── mapper/SaleMapper.java
│
├── inventory/                           # Módulo Inventario (stock total)
│   ├── domain/Inventory.java           # Entidad 1:1 con Product, @Version
│   ├── domain/InventoryAdjustment.java # Audit trail de ajustes manuales
│   ├── repository/InventoryRepository.java
│   ├── repository/InventoryAdjustmentRepository.java
│   ├── service/InventoryService.java   # CRUD + ajustes + integración
│   ├── controller/InventoryController.java  # GET /stock, POST /adjust
│   ├── dto/InventoryResponse.java      # productId, currentStock, threshold
│   ├── dto/AdjustmentRequest.java      # type, quantity con signo, reason
│   ├── mapper/InventoryMapper.java
│   └── exception/
│       ├── InventoryNotFoundException.java
│       └── InvalidAdjustmentException.java
│
├── user/                                # Módulo Usuarios + Gestión
│   ├── domain/User.java
│   ├── domain/Role.java                # Enum: ADMIN, OPERATOR
│   ├── repository/UserRepository.java
│   ├── service/AuthService.java
│   ├── service/UserManagementService.java  # Gestión ADMIN: findAll, changeRole, resetPassword
│   ├── controller/AuthController.java
│   ├── controller/UserManagementController.java  # 4 endpoints ADMIN-only
│   ├── dto/RegisterRequest.java
│   ├── dto/LoginRequest.java
│   ├── dto/LoginResponse.java
│   ├── dto/RefreshRequest.java         # DTO para renovar access token
│   ├── dto/UserResponse.java           # DTO para respuestas de usuario (sin password)
│   ├── dto/ChangeRoleRequest.java      # DTO para cambio de rol
│   ├── dto/ResetPasswordRequest.java   # DTO para reset de contraseña
│   ├── mapper/UserMapper.java          # User → UserResponse
│   └── exception/
│       ├── InvalidRefreshTokenException.java
│       ├── AuthExceptionHandler.java   # @ControllerAdvice con @Order → 401
│       ├── UserNotFoundException.java  # RuntimeException para usuario no encontrado
│       └── UserExceptionHandler.java   # @ControllerAdvice con @Order → 404/400
│
├── security/                            # Configuración Seguridad
│   ├── config/SecurityConfig.java
│   ├── config/CorsConfig.java           # Configuración CORS (CorsConfigurationSource)
│   ├── filter/JwtAuthenticationFilter.java
│   ├── service/JwtService.java
│   └── service/CustomUserDetailsService.java
│
└── common/                              # Compartido
    ├── exception/ProductNotFoundException.java
    ├── exception/ProductAlreadyExistsException.java
    ├── exception/BulkProductNotFoundException.java
    ├── handler/GlobalExceptionHandler.java
    └── dto/ErrorResponse.java
```

### Base de Datos

**Esquema inicial (Flyway V1):**
```
users
├── id (PK, AUTO_INCREMENT)
├── username (VARCHAR, UNIQUE)
├── password (VARCHAR)
└── role (ENUM: ADMIN, OPERATOR)

products
├── id (PK, AUTO_INCREMENT)
├── sku (VARCHAR, UNIQUE)
├── name (VARCHAR)
├── min_price (DECIMAL)
└── active (BOOLEAN)

bulk_products
├── id (PK, AUTO_INCREMENT)
├── name (VARCHAR)
├── stock (DECIMAL)
├── cost (DECIMAL)
└── conversion_ratio (DECIMAL)

production_batches
├── id (PK, AUTO_INCREMENT)
├── product_id (FK → products.id)
├── bulk_product_id (FK → bulk_products.id)
├── initial_quantity (DECIMAL)
├── current_stock (DECIMAL)
├── unit_cost_at_production (DECIMAL)
├── raw_quantity_used (DECIMAL)
├── production_date (TIMESTAMP)
└── version (BIGINT, para @Version)

sales
├── id (PK, AUTO_INCREMENT)
├── total_amount (DECIMAL)
├── created_at (TIMESTAMP)
└── version (BIGINT, para @Version)

sale_items
├── id (PK, AUTO_INCREMENT)
├── sale_id (FK → sales.id)
├── production_batch_id (FK)
├── quantity (INTEGER)
├── unit_price_at_sale (DECIMAL)
└── unit_cost_at_sale (DECIMAL)
```

---

## Patrones de Diseño

### 1. **Layered Architecture (Arquitectura por Capas)**
Cada módulo está dividido en:
- **Controller**: Maneja HTTP, validación de entrada
- **Service**: Lógica de negocio pura
- **Repository**: Acceso a datos (Spring Data JPA)
- **Domain**: Entidades JPA (estado)
- **DTO**: Objetos de transferencia (Request/Response)
- **Mapper**: Conversión Entity ↔ DTO

**¿Por qué?**
- Separación de responsabilidades clara
- Facilita testing (cada capa se testea independientemente)
- Alineado con las convenciones de Spring Boot

### 2. **DTO Pattern (Data Transfer Object)**
Uso de Request/Response DTOs en lugar de exponer las entidades directamente.

**¿Por qué?**
- Control sobre qué datos se exponen (no exponer passwords, campos internos)
- Validación con Jakarta Validation en los Request
- Desacoplamiento: la API puede cambiar sin afectar la entidad

### 3. **Repository Pattern**
Uso de Spring Data JPA repositories en lugar de escribir SQL manual.

**¿Por qué?**
- Reduce boilerplate de acceso a datos
- Métodos derivados del nombre (`findBySku`, `findByActiveTrue`)
- Fácil de testear con H2

### 4. **Mapper Pattern (Manual)**
Conversión manual en lugar de usar MapStruct o ModelMapper.

**¿Por qué?**
- Control total sobre el mapeo
- Sin dependencias extra
- Fácil de debuggear
- Lombok reduce el boilerplate de las entidades

### 5. **Exception Handling Centralizado**
`@ControllerAdvice` (`GlobalExceptionHandler`) para manejar excepciones.

**¿Por qué?**
- Respuestas de error consistentes (HTTP status + body con mensaje)
- Evita try-catch repetitivo en controllers
- Separación de preocupaciones: controller no maneja errores, delega al handler

### 6. **Soft Delete**
En Product: `active` flag en lugar de `DELETE FROM`.

**¿Por qué?**
- Mantiene integridad referencial (production_batches sigue apuntando al producto)
- Permite auditoría y recuperación
- Requisito de negocio: no borrar historial

### 7. **FIFO (First In, First Out)**
En ProductionBatch: `current_stock` por lote para despachar del más antiguo primero.

**¿Por qué?**
- Práctica estándar en inventarios de productos con vencimiento
- Evita pérdidas por productos vencidos

### 8. **Optimistic Locking**
`@Version` en ProductionBatch.

**¿Por qué?**
- Previene race conditions cuando múltiples ventas intentan descontar del mismo lote
- Lanza `OptimisticLockException` si hay conflicto (se reintenta o maneja)

### 9. **JWT Authentication (Stateless)**
Tokens JWT firmados en lugar de sesiones en servidor.

**¿Por qué?**
- Escalable: no requiere estado en el servidor
- Spring Security integra JWT fácilmente
- Ideal para APIs REST

### 10. **Role-Based Access Control (RBAC)**
`@PreAuthorize("hasRole('ADMIN')")` en endpoints sensibles.

**¿Por qué?**
- Bulk products y production batches solo los gestiona ADMIN
- Auth endpoints son públicos
- Fácil de extender (más roles en el enum `Role.java`)

### 11. **Factory Method (implícito en Spring)**
`@Bean` en `SecurityConfig` para crear `SecurityFilterChain`, `AuthenticationManager`, etc.

**¿Por qué?**
- Centraliza la configuración de componentes
- Spring maneja el ciclo de vida
- Fácil de reemplazar beans para testing

### 12. **Singleton (implícito en Spring)**
Todos los `@Service`, `@Controller`, `@Repository` son singletons por defecto.

**¿Por qué?**
- Eficiencia de memoria (una sola instancia)
- Stateless por diseño (no guardan estado en campos)
- Spring garantiza una sola instancia por bean

### 13. **Strategy Pattern (implícito en Security)**
`JwtAuthenticationFilter` como estrategia de autenticación dentro del filter chain.

**¿Por qué?**
- Permite cambiar estrategia de auth sin tocar el negocio
- Spring Security usa chain of filters (cada filter es una estrategia)
- Fácil de agregar OAuth2, Basic Auth, etc. en el futuro

---

## Estado Actual

### Módulos Completados ✅
- [x] Configuración inicial (Spring Boot + Docker + MySQL)
- [x] Migraciones con Flyway
- [x] CI con GitHub Actions
- [x] Perfil de test con H2
- [x] Módulo Productos (CRUD + tests)
- [x] Módulo Materia Prima (CRUD + tests)
- [x] Módulo Lotes de Producción (CRUD + tests)
- [x] Autenticación JWT (registro + login)
- [x] RBAC con roles ADMIN/OPERATOR
- [x] **Módulo de Ventas (FIFO + @Version + 8 integration tests)**
- [x] **CORS Configuration (preflight OPTIONS + CorsConfigurationSource)**
- [x] **Módulo de Inventario (stock total + ajustes + integración con ventas/producción)**
- [x] **Módulo de Gestión de Usuarios (Users Management)** — ADMIN endpoints + tests + docs

### Módulos en Progreso 🚧
- [x] **Auth Refresh Token** (Phases 1-4 Apply ✅ | Pending: Verify + Archive)
- [x] **Users Management** (SDD Apply PR-3 Complete ✅ | Pending: Verify + Archive)
- [ ] Documentación de API con OpenAPI (parcial — necesita agregar Sales, Inventory)

### Tests
- **Unit tests**: `ProductionBatchServiceTest` (4 tests), `SaleMapperTest` (2 tests), `SaleServiceTest` (5 tests), `AuthServiceTest` (4 tests), `InventoryServiceTest` (10 tests), `UserManagementServiceTest` (10 tests)
- **Integration tests**: `ProductControllerIT`, `BulkProductControllerIT`, `ProductionBatchControllerIT`, `SaleControllerIT` (8 tests), `AuthRefreshIT` (1 test), `UserManagementIT` (11 tests)
- **Controller tests**: `AuthControllerTest` (2 tests), `InventoryControllerTest` (7 tests), `UserManagementControllerTest` (12 tests)
- **CORS tests**: `CorsConfigTest` (1 test), `CorsSecurityTest` (3 tests)
- **Total tests (surefire)**: **73** (61 existentes + 12 nuevos controller tests)
- **Total tests con IT**: **84** (73 + 11 integration tests)
- **Coverage**: Pendiente configurar JaCoCo reports

### Branches Actuales
```
main (producción)
├── develop (integración)
    ├── feature/auth-refresh-token (actual - Phases 1-4 Apply complete, Verify pendiente)
    ├── feature/users-management (PR #3 Polish - completado | Verify pendiente)
    ├── feature/cors-configuration (completado + mergeado a develop)
    ├── feature/sales (completado)
    ├── feature/production-batches (completado)
    ├── feature/bulk-products (completado)
    ├── feature/product-module (completado)
    └── feature/inventory-module (PR #3 Integration - completado)
```

---

## Próximos Pasos

### Corto Plazo (1-2 semanas)
1. **Completar módulo Production Batches**
   - [ ] Finalizar tests faltantes
   - [ ] PR a develop
   - [ ] PR develop → main

2. **Módulo de Ventas (FIFO)**
   - [ ] Crear branch `feature/sales`
   - [ ] Entidad `Sale` y `SaleItem`
   - [ ] Lógica FIFO: descontar del lote más antiguo primero
   - [ ] Endpoint POST `/api/v1/sales`
   - [ ] Tests de integración

3. **Mejoras de Seguridad**
   - [ ] Refresh tokens (actualmente solo access token)
   - [ ] Rate limiting en auth endpoints
   - [ ] Validar y sanitizar inputs más allá de Jakarta Validation

### Mediano Plazo (1 mes)
4. **Reportes**
   - [ ] Reporte de inventario actual
   - [ ] Reporte de producción por período
   - [ ] Reporte de ventas
   - [ ] Endpoints con métricas para dashboard

5. **CI/CD**
   - [ ] Afinar GitHub Actions (actualmente solo build + test)
   - [ ] Deploy automático a staging/producción
   - [ ] SonarCloud para análisis de código

### Largo Plazo (2-3 meses)
7. **Frontend**
   - [ ] Desarrollo de SPA (React/Vue/Angular) para administrar inventario
   - [ ] Dashboard con gráficos de ventas y stock

8. **Escalabilidad**
   - [ ] Caching con Redis (consultas frecuentes de productos)
   - [ ] Paginación en endpoints GET (productos, lotes, ventas)
   - [ ] Migrar a PostgreSQL si MySQL se queda corto

9. **Monitoreo**
   - [ ] Spring Boot Actuator
   - [ ] Prometheus + Grafana
   - [ ] Alertas de stock bajo

### Módulos Adicionales (Visión Extendida)

**Módulo: Inventory (Stock de Productos Envasados)** ✅ COMPLETADO
- [x] Gestión del stock de productos ya envasados (3L, 2L, 1.5L, etc.)
- [x] Diferencia clave: `production-batches` maneja LOTES (cuánto se produjo), `inventory` maneja STOCK FINAL (cuánto hay disponible para venta)
- [x] Endpoints: GET `/api/v1/inventory/{productId}`, GET `/api/v1/inventory/low-stock`, POST `/api/v1/inventory/{productId}/adjust`
- [x] Patrones: Repository, DTO, Service + integración con Sales y ProductionBatch
- [x] Integración transaccional: incrementStock al crear lote, decrementStock al crear venta

**Módulo: Notifications (Alertas)**
- [ ] Notificaciones de stock bajo, producción necesaria, etc.
- [ ] Patrones: Observer pattern (publish/subscribe), Strategy pattern para canales (email, SMS, push)
- [ ] Integración con el módulo de Inventory

**Módulo: Users Management (Gestión de Usuarios - ADMIN)** ✅ COMPLETADO
- [x] CRUD de usuarios por parte de ADMIN (listar, detalle, cambio rol, reset password)
- [x] Asignación de roles dinámica (ADMIN ↔ OPERATOR via PATCH /api/v1/users/{id}/role)
- [x] Endpoints: GET `/api/v1/users`, GET `/api/v1/users/{id}`, PATCH `/api/v1/users/{id}/role`, PATCH `/api/v1/users/{id}/password`
- [x] Patrones: Consistente con módulos existentes (Controller → Service → Repository → Domain, DTOs como records, @Component mapper, @ControllerAdvice con @Order)
- [x] Stack: 3 PRs (Foundation → Core → Polish), 33 tests nuevos

**Módulo: Audit Log (Registro de Actividad)**
- [ ] Log de quién hizo qué y cuándo
- [ ] Patrones: Decorator pattern para envolver operaciones con logging, Aspect-Oriented Programming (@Aspect de Spring)
- [ ] Trazabilidad completa para auditoría

**Módulo: Envíos (Logística de Entrega - si aplica)**
- [ ] Gestión de envíos a clientes
- [ ] Patrones: Repository + Service + Controller (consistente)

### Mejoras Técnicas Pendientes
- [ ] Inicializar SDD formalmente (`openspec/`)
- [x] Agregar integration tests para user management
- [ ] Agregar unit tests para services (no solo integration)
- [ ] Implementar pagination en endpoints de lista
- [ ] Agregar versionado de API más robusto
- [ ] Configurar CI/CD pipeline completo (actualmente solo build + test)
- [ ] Agregar cache (Redis) para productos frecuentes
- [ ] Implementar rate limiting en auth endpoints
- [ ] Documentación completa de la API con OpenAPI/Swagger

### Tabla Comparativa: Implementado vs Futuro

| Aspecto | Ya Implementado | A Futuro |
|----------|------------------|-----------|
| Arquitectura | Layered por módulos | Misma base + patrones adicionales |
| Autenticación | JWT básico, 2 roles | JWT refresh tokens, más roles |
| Patrones principales | MVC, DTO, Mapper, Repository | +Strategy, Observer, Builder |
| Transacciones | @Transactional simple | +Saga pattern para flujos complejos |
| Concurrencia | @Version en ProductionBatch, Sale | +Optimistic locking en Inventory |
| Tests | Integration tests + unit tests (Sales, ProductionBatch) | +Unit tests para todos los services, E2E tests |
| Base de datos | MySQL + Flyway | +Redis cache (opcional) |
| Documentación | OpenAPI/Swagger en controllers | +Reportes automáticos |
| Soft delete | Solo en Products | En Sales, Inventory |
| FIFO | Lógica en ProductionBatch | Lógica de descuento en Sales ✅ IMPLEMENTADO |
| SDD | No inicializado formalmente | Formalizar con openspec/ |

### Orden Recomendado de Implementación Futura
1. ~~**Inventory Module**~~ ✅ **COMPLETADO**
2. ~~**Users Management**~~ ✅ **COMPLETADO**
3. **Unit Tests** ← Coverage de services (ProductService, BulkProductService, ProductionBatchService)
3. **Pagination** → Todos los endpoints de lista
4. **Reporting Module** ← Reportes para el admin
5. **Notifications** ← Alertas de stock bajo
6. **Audit Logging** ← Trazabilidad completa
7. **SDD Initialization** ← Para los módulos que vengan después (ya configurado)

---

## Workflow de Desarrollo

### Flujo con Git y PRs

1. **Crear branch desde develop:**
   ```bash
   git checkout develop
   git pull origin develop
   git checkout -b feature/nombre-modulo
   ```

2. **Desarrollo con IA (Gentle AI):**
   - La IA (agente) hace los commits siguiendo Conventional Commits
   - Formato: `type(scope): description`
     - `feat(product): implement CRUD`
     - `fix(auth): resolve token expiration`
     - `test(batch): add integration tests`
     - `chore: update dependencies`

3. **Pull Request a develop:**
   - Vos (el humano) revisás y hacés el PR
   - Merge después de revisión

4. **Pull Request de develop a main:**
   - develop → main para producción
   - Solo cuando el conjunto de features es estable

5. **En otra PC:**
   - Clonar repo
   - Tener Engram configurado (la memoria persiste)
   - Tener skills SDD instaladas
   - `git checkout develop` y continuar

### Convenciones de Commits

| Tipo | Cuándo usar |
|------|-------------|
| `feat` | Nueva funcionalidad |
| `fix` | Corrección de bug |
| `refactor` | Cambio de código sin cambiar funcionalidad |
| `test` | Agregar o modificar tests |
| `chore` | Configuración, dependencias, sin tocar código de la app |
| `docs` | Cambios en documentación |
| `style` | Formato, no cambia lógica (espacios, semicolons) |
| `perf` | Mejora de performance |

---

## Notas sobre SDD (Spec-Driven Development)

Este proyecto usa **SDD** para el flujo de desarrollo con la IA:

### Sales Module
1. **Explore** → ✅
2. **Propose** → ✅
3. **Spec** → ✅
4. **Design** → ✅
5. **Tasks** → ✅
6. **Apply** → ✅ (Phase 1-5 completo, Phase 6 en progreso)
7. **Verify** → ❌ (pendiente)
8. **Archive** → ❌ (pendiente)

### Auth Refresh Token (Sprint 1)
1. **Explore** → ✅
2. **Propose** → ✅
3. **Spec** (6 reqs, 12 scenarios) → ✅
4. **Design** (3 new, 2 modified) → ✅
5. **Tasks** (12 tasks, 4 phases) → ✅
6. **Apply** (Phases 1-4) → ✅ **← ACÁ ESTAMOS**
7. **Verify** → ❌ (pendiente)
8. **Archive** → ❌ (pendiente)

### Inventory Module (PR #3: Integration)
1. **Apply** (Phase 3) → **✅ COMPLETADO**
   - Task 3.1: Wire InventoryService into ProductionBatchService
   - Task 3.2: Wire InventoryService into SaleService
   - Task 3.3: Update README.md
   - Task 3.4: Update PROJECT_HISTORY.md

### Users Management (Sprint 3)
1. **Explore** → ✅
2. **Propose** → ✅
3. **Spec** (4 reqs, 12+ scenarios) → ✅
4. **Design** (8 new, 1 modified, stacked PR plan) → ✅
5. **Tasks** (14 tasks, 3 phases) → ✅
6. **Apply** (Phases 1-3, 3 stacked PRs) → ✅ **← ACÁ ESTAMOS**
7. **Verify** → ❌ (pendiente)
8. **Archive** → ❌ (pendiente)

### Commits del Sales Module
| Commit | Descripción |
|--------|-------------|
| `585a622` | feat(sales): implement Phase1-3 with FIFO logic, hybrid system |
| `3776eeb` | docs(sales): add Spanish comments to all sales module files |
| `18c3478` | feat(sales): implement Phase 4 - Controller & Security with integration tests |
| `bdd330c` | test(sales): implement Phase 5 - Integration Tests (FIFO, stock, concurrency) |

### Descubrimientos Críticos
- **JWT Bug**: `JwtAuthenticationFilter` tenía `username == null` en vez de `!= null`. La autenticación no funcionaba desde el inicio.
- **401 vs 403**: Spring Security retorna 403 por defecto para requests no autenticados. Se agregó `HttpStatusEntryPoint(UNAUTHORIZED)` para comportamiento REST correcto.
- **GlobalExceptionHandler**: Faltaban handlers para `IllegalArgumentException` (400), `AccessDeniedException` (403), y `OptimisticLockingFailureException` (409).
- **@Order en Exception Handlers**: Sin `@Order(Ordered.HIGHEST_PRECEDENCE)`, el `GlobalExceptionHandler` (catch-all Exception → 500) captura `InvalidRefreshTokenException` antes que `AuthExceptionHandler`.

---

**Última actualización:** 2026-05-13 (Sprint 3: Users Management — PR #3 Polish)
**Mantenido por:** zotel1
**IA colaboradora:** Gentle AI (big-pickle)
