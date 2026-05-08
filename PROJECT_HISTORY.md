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
├── user/                                # Módulo Usuarios
│   ├── domain/User.java
│   ├── domain/Role.java                # Enum: ADMIN, OPERATOR
│   ├── repository/UserRepository.java
│   ├── service/AuthService.java
│   ├── controller/AuthController.java
│   ├── dto/RegisterRequest.java
│   ├── dto/LoginRequest.java
│   └── dto/LoginResponse.java
│
├── security/                            # Configuración Seguridad
│   ├── config/SecurityConfig.java
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

### Módulos en Progreso 🚧
- [ ] Documentación de API con OpenAPI (parcial — necesita agregar Sales)

### Tests
- **Unit tests**: `ProductionBatchServiceTest`, `SaleMapperTest`, `SaleServiceTest`
- **Integration tests**: `ProductControllerIT`, `BulkProductControllerIT`, `ProductionBatchControllerIT`, `SaleControllerIT` (8 tests)
- **Total tests passing**: 10 unit tests + 8 integration tests (verificados individualmente)
- **Coverage**: Pendiente configurar JaCoCo reports

### Branches Actuales
```
main (producción)
├── develop (integración)
    ├── feature/sales (actual - Phase 5 COMPLETE, Phase 6 pendiente)
    ├── feature/production-batches (completado)
    ├── feature/bulk-products (completado)
    └── feature/product-module (completado)
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

5. **Gestión de Usuarios**
   - [ ] Endpoint para listar usuarios (ADMIN)
   - [ ] Cambio de rol (ADMIN promueve OPERATOR)
   - [ ] Reset de password

6. **CI/CD**
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

**Módulo: Inventory (Stock de Productos Envasados)**
- [ ] Gestión del stock de productos ya envasados (3L, 2L, 1.5L, etc.)
- [ ] Diferencia clave: `production-batches` maneja LOTES (cuánto se produjo), `inventory` maneja STOCK FINAL (cuánto hay disponible para venta)
- [ ] Endpoints: GET `/api/v1/inventory`, POST `/api/v1/inventory/{id}/adjust`, GET `/api/v1/inventory/low-stock`
- [ ] Patrones: Repository, DTO, Service + Observer pattern (alertas de stock bajo)

**Módulo: Notifications (Alertas)**
- [ ] Notificaciones de stock bajo, producción necesaria, etc.
- [ ] Patrones: Observer pattern (publish/subscribe), Strategy pattern para canales (email, SMS, push)
- [ ] Integración con el módulo de Inventory

**Módulo: Users Management (Gestión de Usuarios - ADMIN)**
- [ ] CRUD de usuarios por parte de ADMIN
- [ ] Asignación de roles dinámica
- [ ] Endpoint: GET `/api/v1/users`, PUT `/api/v1/users/{id}/role`
- [ ] Patrones: Consistente con módulos existentes

**Módulo: Audit Log (Registro de Actividad)**
- [ ] Log de quién hizo qué y cuándo
- [ ] Patrones: Decorator pattern para envolver operaciones con logging, Aspect-Oriented Programming (@Aspect de Spring)
- [ ] Trazabilidad completa para auditoría

**Módulo: Envíos (Logística de Entrega - si aplica)**
- [ ] Gestión de envíos a clientes
- [ ] Patrones: Repository + Service + Controller (consistente)

### Mejoras Técnicas Pendientes
- [ ] Inicializar SDD formalmente (`openspec/`)
- [ ] Agregar integration tests para auth
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
1. **Inventory Module** ← Necesario para saber cuánto hay disponible
2. **Unit Tests** ← Coverage de services (ProductService, BulkProductService, ProductionBatchService)
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

1. **Explore** → Investigar requerimientos ✅ (ventas)
2. **Propose** → Propuesta de cambio ✅ (ventas)
3. **Spec** → Especificaciones (Given/When/Then) ✅ (ventas)
4. **Design** → Diseño técnico ✅ (ventas)
5. **Tasks** → Tareas de implementación ✅ (ventas)
6. **Apply** → Codear ✅ (Phase 1-5 completo, Phase 6 en progreso)
7. **Verify** → Verificar contra specs ❌ (pendiente)
8. **Archive** → Cerrar change ❌ (pendiente)

La memoria persiste en **Engram**, lo que permite retomar en otra PC sin perder contexto.

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

---

**Última actualización:** Mayo 2026
**Mantenido por:** zotel1
**IA colaboradora:** Gentle AI (big-pickle)
