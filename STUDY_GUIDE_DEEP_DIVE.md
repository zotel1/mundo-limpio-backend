# 🧠 Estudio Profundo — MundoLimpio Backend

> **Sesión**: 15 de Mayo 2026 — Auditoría completa del proyecto
> **Propósito**: Guía de estudio con explicaciones detalladas de TODOS los conceptos técnicos del backend
> **Estilo**: Técnico + Analogías — para aprender de verdad

---

## Índice

1. [Transacciones: COMMIT / ROLLBACK / @Transactional](#1-transacciones)
2. [Optimistic Locking (@Version)](#2-optimistic-locking)
3. [JwtAuthenticationFilter — El Interceptor de Requests](#3-jwtauthenticationfilter)
4. [FIFO — Algoritmo de Inventario](#4-fifo)
5. [CORS — Cross-Origin Resource Sharing](#5-cors)
6. [Auth Refresh Token](#6-auth-refresh-token)
7. [Módulo de Inventario](#7-módulo-de-inventario)
8. [Users Management](#8-users-management)
9. [JPA / ORM — ¿Qué es?](#9-jpa--orm)
10. [BigDecimal vs Double](#10-bigdecimal-vs-double)
11. [¿Qué es Boilerplate?](#11-boilerplate)
12. [Flyway y Backup](#12-flyway-y-backup)
13. [Evolución del Proyecto — Rama por Rama](#13-evolución-del-proyecto)
14. [Patrones de Diseño Reconocibles](#14-patrones-de-diseño)
15. [Glosario Rápido](#15-glosario-rápido)

---

## 1. Transacciones

### 1.1 El Problema

Cuando hacés varias operaciones en la base de datos (ej: crear venta + descontar stock + registrar pago), si una falla a la mitad, los datos quedan inconsistentes. El dinero se debita pero el stock no se descuenta.

### 1.2 ¿Qué son BEGIN / COMMIT / ROLLBACK?

Son instrucciones SQL para manejar transacciones a bajo nivel:

| Instrucción | ¿Qué hace? |
|-------------|------------|
| `BEGIN TRANSACTION` | Arranca una transacción. Los cambios son temporales y SOLO los ve quien los hizo |
| `COMMIT` | Confirma TODO. Los cambios se vuelven permanentes y visibles para todos |
| `ROLLBACK` | Deshace TODO. Como si nunca hubiera pasado. |

```sql
BEGIN TRANSACTION;
UPDATE production_batches SET current_stock = 7 WHERE id = 1;
INSERT INTO sales (total_amount) VALUES (150.00);
-- Si todo va bien:
COMMIT;  → ✅ Datos guardados para siempre
-- Si algo falla:
ROLLBACK;  → 💥 Todo lo anterior se deshace
```

### 1.3 ¿Qué hace `@Transactional`?

Spring maneja TODO automáticamente:

```java
@Transactional
public void crearVenta() {
    // Spring hace: connection.beginTransaction()  ← arranca
    
    repo1.descontarStock();   // SQL UPDATE
    repo2.crearVenta();       // SQL INSERT
    repo3.crearItems();       // SQL INSERT
    
    // Si todo OK → Spring hace: connection.commit()
    // Si cualquier excepción → Spring hace: connection.rollback()
}
```

**Sin `@Transactional`**: cada llamada al repositorio hace su propio `COMMIT` implícito. Si la segunda falla, la primera ya se guardó y no hay vuelta atrás.

### 1.4 ⏪ Analogía

Estás **haciendo una torta**:
- **`BEGIN TRANSACTION`** = Ponés todos los ingredientes en la mesada
- **`COMMIT`** = Metés la torta al horno. Ya no hay vuelta atrás
- **`ROLLBACK`** = Te diste cuenta que pusiste sal en vez de azúcar. Tirás todo y empezás de nuevo

Sin transacciones, sería como meter **cada ingrediente al horno por separado**: un huevo cocido, harina cruda, leche fría. Un desastre.

### 1.5 📍 En el código

- `SaleService.java` — `@Transactional` en `createSale()`
- `ProductionBatchService.java` — `@Transactional` en `createBatch()`
- `InventoryService.java` — `@Transactional` en `adjustStock()`

---

## 2. Optimistic Locking

### 2.1 El Problema

Dos ADMIN venden al mismo tiempo. Los dos leen `stock=10`. El primero vende 3 → pone `stock=7`. El segundo vende 5 → pone `stock=5`. **¡Perdimos 2 unidades!** El segundo sobreescribió al primero. Esto es una **race condition**.

### 2.2 La Solución: `@Version`

```java
@Entity
public class ProductionBatch {
    @Version
    private Long version;  // ← Arranca en 0, JPA lo incrementa automáticamente
    private BigDecimal currentStock;
}
```

**Cómo funciona:**

```
Tú lees:  version=0, stock=10
Otro lee: version=0, stock=10

Tú vendes 3 → UPDATE stock=7 WHERE id=X AND version=0 → ✅ (versión pasa a 1)
Otro vende 5 → UPDATE stock=5 WHERE id=X AND version=0 → ❌ FALLA! (la version ya es 1)
```

El segundo recibe `OptimisticLockingFailureException`. El `@Transactional` hace rollback. El usuario recibe HTTP 409 Conflict.

### 2.3 ¿Se configura en MySQL o en Java?

**SOLO en Java.** JPA se encarga de TODO:
- Agrega la columna `version` en la tabla (Flyway ya la tiene en `production_batches` y `sales`)
- Incrementa automáticamente en cada UPDATE
- Verifica que la versión no haya cambiado antes de actualizar

No necesitás triggers, ni procedures, ni nada en MySQL.

### 2.4 ⏪ Analogía

Es como **editar un documento de Google Docs** con alguien más. Si los dos editan la misma línea, Google Docs avisa "hay un conflicto". Nadie pierde datos, pero alguien tiene que resolverlo.

Sin `@Version` sería como tener un archivo local, editarlo por separado, y el que guarda último SOBREESCRIBE todo lo que hizo el otro.

### 2.5 📍 En el código

- `ProductionBatch.java` — `@Version private Long version;`
- `Sale.java` — `@Version private Long version;`
- `Inventory.java` — `@Version private Long version;`

---

## 3. JwtAuthenticationFilter

### 3.1 El Problema

HTTP es **stateless** (sin estado). Cada request es independiente. El backend necesita saber **quién sos** y **si estás autorizado** en cada request, pero no hay una "sesión" persistente.

### 3.2 La Solución: JWT

Cuando te logueás, el backend te da un **token JWT firmado**. Contiene: username, rol, expiración. Está FIRMADO criptográficamente — nadie puede falsificarlo.

Cada request manda el token en el header:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

### 3.3 La Cadena de Filtros de Spring Security

```
Request del frontend (Flutter)
       ↓
  [SecurityContextFilter]
  [JwtAuthenticationFilter]  ← ACÁ INTERCEPTAMOS
  [AuthorizationFilter]      ← Verifica @PreAuthorize
  [... otros filtros ...]
       ↓
  Controller (ProductController, AuthController, etc.)
```

`JwtAuthenticationFilter` hace:

1. Atrapa el request ANTES de que llegue al controller
2. Lee el header `Authorization: Bearer <token>`
3. Valida el token (firma, expiración, formato) usando `JwtService`
4. Extrae el username del token
5. Busca el usuario en la DB (`CustomUserDetailsService`)
6. Crea un `UsernamePasswordAuthenticationToken` (el objeto que Spring Security usa para identificar al usuario)
7. Lo setea en el `SecurityContextHolder` (le dice a Spring: "este request es del usuario X con rol Y")
8. Pasa el request al siguiente filtro

### 3.4 ¿Intercepta todos los requests del frontend?

SÍ. TODOS los requests HTTP pasan por la cadena de filtros. Pero NO todos necesitan autenticación:

```java
// SecurityConfig.java
.requestMatchers("/api/v1/auth/**").permitAll()        // ← login, register, refresh: PÚBLICOS
.requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
.anyRequest().authenticated()                           // ← todo lo demás: requiere token
```

**Login** no requiere token (no tenés uno todavía). El filtro ve que no hay token y simplemente sigue de largo. El request llega al `AuthController.login()` sin problemas.

**Logout**: no hay endpoint de logout porque es JWT stateless. El frontend simplemente descarta el token. Para invalidar tokens habría que implementar una blacklist (Redis o DB).

### 3.5 ⏪ Analogía

El backend es un **edificio de oficinas con seguridad**:

- **JwtAuthenticationFilter** = el **guardia de la entrada**
- **Token JWT** = tu **carnet de empleado**
- **Controllers** = las **oficinas**

Cada persona que entra pasa por el guardia. Revisa el carnet, verifica que sea válido (no esté vencido), y te deja pasar.

- Si vas a **Recepción** (`/api/v1/auth/login`), no necesitas carnet — es público
- Si vas a **Ventas** (`/api/v1/sales`), necesitas carnet y además ser ADMIN

El guardia NO decide si podés entrar a una oficina específica, solo verifica tu identidad. La autorización (ADMIN vs OPERATOR) la hace OTRO filtro después.

### 3.6 📍 En el código

- `security/filter/JwtAuthenticationFilter.java` — el filtro mismo
- `security/service/JwtService.java` — genera y valida tokens
- `security/service/CustomUserDetailsService.java` — busca usuario en DB
- `security/config/SecurityConfig.java` — configura la cadena de filtros

---

## 4. FIFO

### 4.1 El Problema

Cuando vendés un producto, ¿de qué lote lo sacás? Si produjiste 100 unidades el lunes y 100 el viernes, y llega un cliente a comprar 50, ¿cuál usás?

En la industria, SIEMPRE se vende primero lo más viejo (para evitar vencimientos).

### 4.2 ¿Librería o Algoritmo Custom?

**ES 100% CÓDIGO NUESTRO.** No usa librerías. Es un algoritmo implementado en `SaleService.java`.

### 4.3 Cómo Funciona

```java
private void processFifoDeduction(Product product, BigDecimal quantity) {
    // 1. Buscar lotes ordenados por fecha ASC (más viejo primero)
    List<ProductionBatch> batches = batchRepository
        .findByProductIdOrderByProductionDateAsc(product.getId());

    BigDecimal remaining = quantity;

    for (ProductionBatch batch : batches) {
        if (remaining <= 0) break;

        // 2. De cada lote, tomar lo que se pueda
        BigDecimal deduct = min(remaining, batch.getCurrentStock());
        batch.setCurrentStock(batch.getCurrentStock() - deduct);
        remaining = remaining - deduct;

        // 3. Crear SaleItem con snapshot de costos
        // 4. Si el lote se agotó, pasar al siguiente
    }
}
```

### 4.4 ⏪ Analogía

Tenés una **heladera con bebidas**. Las que compraste primero están atrás, las nuevas adelante. Cuando agarrás una bebida, **siempre agarrás la más vieja** (la de atrás) para que no se venza. Eso es FIFO.

### 4.5 📍 En el código

- `sales/service/SaleService.java` — método `processFifoDeduction()` y `calculateTotalAmount()`

---

## 5. CORS

### 5.1 El Problema

Tu backend corre en `http://localhost:8080`. Tu frontend Flutter corre en el emulador Android. Son **orígenes diferentes**. El navegador (y algunos clientes HTTP) NO permiten que un origen llame a otro sin autorización explícita. Esto es la **Same-Origin Policy**.

Además, hay un paso previo llamado **preflight**: antes del request real, el navegador manda un `OPTIONS` para "preguntar permisos".

### 5.2 Decisión Clave: `CorsConfigurationSource` vs `WebMvcConfigurer`

| Alternativa | 👍 | 👎 |
|-------------|----|-----|
| **`CorsConfigurationSource`** (filtro) | Corre ANTES de auth. El preflight bypassea autenticación | Configuración más verbosa |
| `WebMvcConfigurer` (interceptor) | Más simple | Corre DESPUÉS de auth → el preflight NUNCA llega (pide token antes de responder CORS) |

Elegimos `CorsConfigurationSource` porque el preflight OPTIONS necesita ser respondido SIN token. Es un request de "permiso" que hace el browser antes de mandar el request real.

### 5.3 Cómo se ve en el código

```java
// CorsConfig.java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(Arrays.asList(
        "http://localhost:8080",        // iOS simulator
        "http://10.0.2.2:8080"          // Android emulator (10.0.2.2 = localhost del host)
    ));
    config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type"));
    config.setAllowCredentials(false);  // JWT Bearer token, NO cookies
    config.setMaxAge(3600L);            // Cache del preflight por 1 hora
    
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
}
```

### 5.4 ⏪ Analogía

Querés entrar a una **fiesta privada**:

1. El **portero** (backend) revisa que estés en la lista
2. Pero ANTES, mandás a tu **amigo** a preguntar: "che, se permite campera?" — esto es el **preflight OPTIONS**
3. Tu amigo NO tiene entrada todavía, pero el portero igual le responde: "sí, campera permitida, pero necesitás entrada para entrar"
4. Después vos llegás con tu entrada (token JWT) y entrás

Si el portero le pidiera entrada a tu amigo solo para preguntar, tu amigo nunca podría preguntar nada. Eso pasaba con `WebMvcConfigurer`.

### 5.5 📍 En el código

- `security/config/CorsConfig.java` — bean `CorsConfigurationSource`
- `security/config/SecurityConfig.java` — `.cors(cors -> cors.configurationSource(...))`
- `application.yml` — `application.cors.allowed-origins`

---

## 6. Auth Refresh Token

### 6.1 El Problema

Los tokens JWT tienen **expiración** por seguridad (5 minutos). Si un token es robado, el atacante solo puede usarlo por poco tiempo.

¿Qué pasa cuando expira? El usuario necesita otro sin loguearse de nuevo. Ahí entra el **refresh token** (7 días).

### 6.2 Cómo Funciona

```
LOGIN:
  POST /api/v1/auth/login
  → Te devuelve DOS tokens:
    - accessToken  (expira en 5 minutos)  → para autenticar requests
    - refreshToken (expira en 7 días)     → SOLO para renovar accessToken

CUANDO EXPIRA EL ACCESS TOKEN:
  POST /api/v1/auth/refresh
  Body: { "refreshToken": "token_largo" }
  → Te devuelve UN NUEVO PAR de tokens
```

### 6.3 Decisión Clave: `@Order` en ExceptionHandlers

```java
@ControllerAdvice(basePackages = "com.mundolimpio.application.user")
@Order(Ordered.HIGHEST_PRECEDENCE)          // ← CLAVE: se evalúa PRIMERO
public class AuthExceptionHandler { ... }

@ControllerAdvice                           // ← Sin @Order = prioridad más baja
public class GlobalExceptionHandler { ... } //   Catch-all: Exception.class
```

**¿Por qué?** `InvalidRefreshTokenException` extiende `RuntimeException` → cae en el catch-all `Exception.class` del `GlobalExceptionHandler`. Sin `@Order`, Spring evalúa primero el genérico y devuelve 500 en vez de 401.

### 6.4 ⏪ Analogía

El access token es una **pulsera de fiesta** que dura 5 minutos. El refresh token es tu **DNI**:

1. Cuando la pulsera se vence, vas a **recepción** (endpoint `/refresh`)
2. Mostrás tu DNI (refresh token)
3. Te dan una **pulsera nueva** y te **renuevan el DNI**

Sin refresh token, cada 5 minutos tendrías que poner usuario y contraseña de nuevo. Un embole.

### 6.5 📍 En el código

- `user/dto/RefreshRequest.java` — DTO con `@NotBlank String refreshToken`
- `user/exception/InvalidRefreshTokenException.java` — excepción con enum `RefreshError`
- `user/exception/AuthExceptionHandler.java` — `@ControllerAdvice` con `@Order(HIGHEST_PRECEDENCE)`
- `user/service/AuthService.java` — método `refresh()`
- `user/controller/AuthController.java` — endpoint `POST /refresh`

---

## 7. Módulo de Inventario

### 7.1 El Problema

Antes del inventario, el stock total se calculaba así:

```sql
SELECT SUM(current_stock) FROM production_batches WHERE product_id = 1;
```

Esto está mal porque:
- No hay stock "total" rápido de consultar
- No se puede definir un **umbral mínimo** de stock
- No hay **ajustes manuales** (si se rompen botellas, no podés descontarlas)

### 7.2 La Solución: Entidad `Inventory` separada

**NO** le agregamos un campo `stock` a `Product` porque son responsabilidades diferentes:

- `Product` = **catálogo** (nombre, SKU, precio) — no contamina
- `Inventory` = **operacional** (stock total, umbral, ajustes)

### 7.3 Diferencias Clave

| Concepto | ¿Qué es? | ¿Dónde vive? |
|----------|----------|--------------|
| **Stock por lote** | Cuánto queda de CADA lote (para FIFO) | `ProductionBatch.currentStock` |
| **Stock total** | Cuánto hay del producto en total | `Inventory.currentStock` |

Ambos se actualizan en la **misma transacción `@Transactional`**.

### 7.4 Integración entre Módulos

```java
@Transactional
public SaleResponse createSale(SaleRequest request) {
    // 1. Lógica FIFO (descuenta de lotes)
    // 2. Guarda Sale + SaleItems
    // 3. inventoryService.decrementStock(productId, cantidad)
    // Si algo falla → rollback de TODO
}
```

**`incrementStock`/`decrementStock`** son públicos (no endpoints REST) porque `ProductionBatchService` y `SaleService` están en paquetes diferentes.

### 7.5 Audit Trail vs Evento de Negocio

| Operación | ¿Crea InventoryAdjustment? | ¿Por qué? |
|-----------|---------------------------|-----------|
| `incrementStock` (al crear lote) | ❌ NO | La auditoría es el lote mismo |
| `decrementStock` (al vender) | ❌ NO | La auditoría es la venta misma |
| `adjustStock` (ajuste manual) | ✅ SÍ | Acción ADMIN que necesita justificación |

### 7.6 ⏪ Analogía

Un **depósito de bebidas**:

- Los **palets** (`production_batches`) = los lotes individuales que entran
- El **estante** (`inventory`) = el total de cada producto

Cuando llega un palet nuevo → anotás "OK, hay +10". Cuando vendés → anotás "OK, hay -3". El estante te dice el total rápido. Los palets te dicen exactamente qué lote está disponible.

Si se rompen botellas, el jefe hace un **ajuste manual** anotando "se rompieron 2".

### 7.7 📍 En el código

- `inventory/domain/Inventory.java` — entidad 1:1 con Product
- `inventory/domain/InventoryAdjustment.java` — audit trail
- `inventory/service/InventoryService.java` — lógica: get, adjust, increment, decrement
- `inventory/controller/InventoryController.java` — endpoints GET + POST adjust
- `inventory/dto/` — `InventoryResponse`, `AdjustmentRequest`
- `inventory/mapper/InventoryMapper.java`
- `inventory/exception/` — `InventoryNotFoundException`, `InvalidAdjustmentException`

---

## 8. Users Management

### 8.1 El Problema

Solo existía `AuthService` para register/login/refresh. No había forma de que un ADMIN:
- Liste todos los usuarios
- Vea el detalle de un usuario
- Cambie el rol de un usuario
- Reseteé la contraseña

### 8.2 Decisión Clave: Separación de AuthService (SRP)

| Servicio | Responsabilidad | Acceso |
|----------|----------------|--------|
| `AuthService` | Autenticación (register, login, refresh) | Público (`permitAll`) |
| `UserManagementService` | Gestión ADMIN (findAll, changeRole, resetPassword) | ADMIN-only |

Si se mezclaran, el controlador tendría endpoints públicos y ADMIN-only mezclados en el mismo lugar. Mala práctica.

### 8.3 Self-Demotion Guard

**Problema**: un ADMIN podría cambiarse el rol a OPERATOR y quedarse sin acceso.

**Solución**: el controller extrae el usuario autenticado del `SecurityContext` y verifica que no se esté cambiando a sí mismo:

```java
// EN EL CONTROLLER (NO en el service)
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
Long currentUserId = userManagementService.getUserIdByUsername(auth.getName());

if (currentUserId.equals(userId)) {
    throw new IllegalArgumentException("SELF_DEMOTION");
}
```

¿Por qué en el controller? Porque el service NO debería depender de `SecurityContextHolder` (acoplamiento a Spring Security). Pasándole el ID como parámetro, el service es testeable con Mockito sin mockear seguridad.

### 8.4 PATCH y httpclient5

**Problema**: `TestRestTemplate` usa `HttpURLConnection` (JDK nativo) que **NO soporta PATCH**.

**Solución**: agregar `httpclient5` (test scope). Spring Boot detecta Apache HttpClient 5 en el classpath y cambia automáticamente a `HttpComponentsClientHttpRequestFactory`, que sí soporta PATCH.

### 8.5 Validación en Cascada

```
1. Jakarta Validation en DTOs (@NotBlank, @Size)
   → Errores estructurales → VALIDATION_ERROR (400)

2. Service valida reglas de negocio
   → Rol inválido, autodemoción → INVALID_ROLE, SELF_DEMOTION (400)

3. UserExceptionHandler captura ambas
   → @Order(HIGHEST_PRECEDENCE) → antes que GlobalExceptionHandler
```

### 8.6 ⏪ Analogía

Un **edificio**:
- `AuthService` = **Recepción**: atiende al público, registra visitas, da pases
- `UserManagementService` = **RRHH**: gestiona empleados, cambia puestos, resetea credenciales

Si pusieras a RRHH en la recepción, sería un lío. Mejor oficinas separadas.

### 8.7 📍 En el código

- `user/service/UserManagementService.java` — lógica ADMIN
- `user/controller/UserManagementController.java` — 4 endpoints ADMIN-only
- `user/dto/UserResponse.java`, `ChangeRoleRequest.java`, `ResetPasswordRequest.java`
- `user/mapper/UserMapper.java`
- `user/exception/UserNotFoundException.java`, `UserExceptionHandler.java`

---

## 9. JPA / ORM

### 9.1 ¿Qué es un ORM?

**ORM** (Object-Relational Mapping) = puente entre el mundo **orientado a objetos** (Java) y el mundo **relacional** (MySQL).

### 9.2 Sin ORM vs Con ORM

**Sin ORM** (SQL a manopla):
```java
String sql = "SELECT * FROM products WHERE id = ?";
PreparedStatement stmt = connection.prepareStatement(sql);
stmt.setLong(1, 1L);
ResultSet rs = stmt.executeQuery();
Product p = new Product();
p.setId(rs.getLong("id"));
p.setName(rs.getString("name"));
// ... 15 líneas más para UN solo SELECT
```

**Con JPA**:
```java
Product p = productRepository.findById(1L).orElseThrow();
// Eso es TODO. JPA genera el SQL, ejecuta la query, mapea el resultado.
```

### 9.3 ¿JPA es una librería?

**NO.** JPA es una **especificación** (interfaces). **Hibernate** es la implementación que Spring Boot usa por defecto.

```
JPA (especificación) ── define "qué hacer"
     ↓ implementa
Hibernate ── hace el trabajo pesado
     ↓
MySQL ── guarda los datos
```

### 9.4 Cómo mapea

```java
@Entity                     // ← Le dice a JPA: "esta clase es una tabla"
@Table(name = "products")   // ← Opcional: especifica el nombre de la tabla
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;         // ← Se mapea a la columna "id"
    
    @Column(unique = true)
    private String sku;      // ← Se mapea a la columna "sku"
    
    private String name;     // ← Sin @Column = misma nombre que el campo
}
```

```sql
-- JPA/Hibernate genera automáticamente:
SELECT id, sku, name, min_price, active FROM products WHERE sku = ?
```

### 9.5 ⏪ Analogía

JPA es como un **traductor simultáneo** entre dos personas que hablan idiomas diferentes:

- Java habla en **objetos** (clases, herencia, métodos)
- MySQL habla en **tablas** (filas, columnas, JOINs)

JPA/Hibernate traduce: "cuando digas `productRepository.findById(1)`, yo lo convierto a `SELECT * FROM products WHERE id = 1` y te devuelvo un objeto Product ya armado".

---

## 10. BigDecimal vs Double

### 10.1 El Problema

`double` y `float` son **inexactos** para decimales. No es un bug, es cómo funciona la aritmética de punto flotante en TODOS los lenguajes:

```java
double a = 0.1;
double b = 0.2;
double c = a + b;
System.out.println(c);  // → 0.30000000000000004  ☠️
```

Esto pasa porque las computadoras trabajan en **binario** (base 2), y `0.1` en decimal es un número **periódico** en binario (como `1/3 = 0.33333...` en decimal).

### 10.2 Por qué BigDecimal

```java
BigDecimal a = new BigDecimal("0.1");  // ← SIEMPRE con String, nunca con double
BigDecimal b = new BigDecimal("0.2");
BigDecimal c = a.add(b);  // → 0.3 EXACTO
```

| Tipo | Precisión | ¿Para dinero? |
|------|-----------|---------------|
| `double` | ~15 dígitos decimales, pero con error de redondeo | ❌ NO |
| `BigDecimal` | Precisión arbitraria y exacta | ✅ SIEMPRE |

### 10.3 Regla de ORO

**Dinero → `BigDecimal`. Siempre. Sin excepción.**

Ni un centavo de más ni de menos por "error de redondeo".

### 10.4 📍 En el código

```java
// Sale.java - línea 39
/**
 * Usamos BigDecimal en vez de Double porque
 * dinero SIEMPRE requiere precisión exacta
 */
private BigDecimal totalAmount;

// SaleRequest.java - línea 16
/**
 * BigDecimal evita problemas de precisión
 * que tendríamos con double/float.
 */
BigDecimal quantity;
```

---

## 11. Boilerplate

### 11.1 ¿Qué es?

**Boilerplate** es código repetitivo que tenés que escribir una y otra vez, sin aportar valor directo a la lógica de negocio.

### 11.2 Ejemplo: Getters y Setters

**SIN Lombok**:
```java
public class Product {
    private Long id;
    private String name;
    
    // Esto es boilerplate para CADA campo:
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Product() {}
    public Product(Long id, String name) { this.id = id; this.name = name; }
    // equals(), hashCode(), toString()...
}
```

**CON Lombok** (lo que usamos):
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    private Long id;
    private String name;
}
```

**Lombok genera todo el boilerplate** en compilación. El código compilado tiene getters, setters, constructores, equals, hashCode, toString — pero vos no escribís nada.

### 11.3 Otros ejemplos en el proyecto

| Herramienta | Sin ella (boilerplate) | Con ella |
|-------------|----------------------|----------|
| Lombok `@Data` | Getters + setters + equals + hashCode + toString a mano | Una anotación |
| Spring Data JPA | SQL + PreparedStatement + ResultSet manual | `findBySku(String sku)` |
| `@ControllerAdvice` | Try-catch en cada controller | Un handler centralizado |

---

## 12. Flyway y Backup

### 12.1 ¿Flyway hace backup de datos?

**NO.** Flyway es para **migraciones de schema** (crear tablas, agregar columnas), no para **datos**.

Si la base de datos se reinicia al redeployar, el problema NO es Flyway. Es que la DB es **volátil** (probablemente un contenedor Docker sin volumen persistente).

### 12.2 Soluciones

| Opción | ¿Qué hace? | ¿Cuándo usarla? |
|--------|-----------|-----------------|
| **Volumen persistente en Docker** | Los datos sobreviven aunque el contenedor se reinicie | ✅ Producción |
| **Flyway `afterMigrate` + SQL de seed** | Inserta datos de prueba después de migrar | ⚠️ Solo desarrollo |
| **`mysqldump`** | Backup programado de la DB real | 🔄 Producción |

### 12.3 Para desarrollo

La solución más común es tener un script SQL con datos de prueba que se ejecuta después de cada migración. No es un backup — es un "reseteo controlado" con datos conocidos.

### 12.4 Para producción

Usar **volúmenes persistentes** en Docker. Los datos se guardan en el disco, no en el contenedor. Si reiniciás el contenedor, los datos siguen ahí. Si borrás y levantás otro apuntando al mismo volumen, también.

---

## 13. Evolución del Proyecto

### Línea de Tiempo Completa

| Fecha | Fase | Branch | Módulo | ¿Qué se decidió? |
|-------|------|--------|--------|-------------------|
| 3-5 Mar | 0 | `project-setup` → `flyway-config` → `test-profile` | Setup | MySQL + Flyway (no ddl-auto), H2 en tests, Java 21, CI |
| 9-18 Mar | 1 | `product-module` | Productos | Soft Delete, SKU único, patrón Controller→Service→Repository, Mapper manual, GlobalExceptionHandler |
| 2-4 May | 2 | `bulk-products` | Materia Prima | conversionRatio, @PreAuthorize ADMIN |
| 2 May | 3 | `segurity-and-flyway` | JWT + Seguridad | JWT (stateless), 2 roles (ADMIN/OPERATOR), SecurityConfig |
| 4-6 May | 4 | `production-batches` | Lotes | FIFO, @Version (optimistic locking), stock por lote |
| 6-8 May | 5 | `sales` | Ventas | Primer SDD completo, FIFO real, snapshot de costos, @Transactional, bugs de JWT corregidos |
| 8-9 May | 6 | `cors-configuration` | CORS | CorsConfigurationSource (filtro, no interceptor), preflight OPTIONS |
| 9-10 May | 7 | `auth-refresh-token` | Refresh Token | POST /refresh, @Order(HIGHEST_PRECEDENCE) en ExceptionHandlers |
| 12 May | 8 | `inventory-module` (3 stacked PRs) | Inventario | Entidad 1:1 con Product, audit trail en ajustes manuales, integración transaccional con Sales y ProductionBatch |
| 13 May | 9 | `users-management` (3 stacked PRs) | Users Mgmt | Separación SRP de AuthService, self-demotion guard, httpclient5 para PATCH |

### Árbol de Branches

```
main (producción)
  └── develop (integración)
       ├── feature/product-module → PR #7
       ├── feature/bulk-products → PR #9
       ├── feature/production-batches → PR #11
       ├── feature/sales → PR #13
       ├── feature/cors-configuration → PR #15
       ├── feature/auth-refresh-token → PR #17
       ├── feature/inventory-module → PRs #19, #21, #22 (stacked)
       └── feature/users-management → PRs #24, #25, #26 (stacked)
```

### Bugs y Gotchas Encontrados

1. **JWT filter bug**: `username == null` en vez de `!= null` → auth nunca funcionaba
2. **403 vs 401**: Sin `HttpStatusEntryPoint(UNAUTHORIZED)`, requests no autenticados daban 403
3. **`@Order` en ExceptionHandlers**: Sin `@Order(HIGHEST_PRECEDENCE)`, el catch-all devuelve 500
4. **HttpURLConnection sin PATCH**: JDK no soporta PATCH → necesitamos httpclient5
5. **CorsFilter no short-circuita**: Spring Security agrega headers CORS pero CONTINÚA la cadena. Necesitás permitir OPTIONS explícitamente.

---

## 14. Patrones de Diseño

| Patrón | ¿Dónde lo usamos? | Propósito |
|--------|-------------------|-----------|
| **Layered Architecture** | Todo el proyecto — Controller → Service → Repository | Separar responsabilidades por capa |
| **DTO Pattern** | Request/Response separados de entidades JPA | No exponer la DB en la API |
| **Repository Pattern** | Spring Data JPA | Abstraer el acceso a datos |
| **Mapper Pattern (manual)** | Entity ↔ DTO manual | Control total, sin dependencias extra |
| **Exception Handling Centralizado** | `@ControllerAdvice` + `GlobalExceptionHandler` | Respuestas de error consistentes |
| **Soft Delete** | `Product.active` | No borrar, mantener integridad referencial |
| **FIFO** | `SaleService.processFifoDeduction()` | Vender lo más viejo primero |
| **Optimistic Locking** | `@Version` en ProductionBatch, Sale, Inventory | Prevenir race conditions |
| **Strategy Pattern** | `JwtAuthenticationFilter` en la cadena de Spring Security | Estrategia de autenticación intercambiable |
| **Singleton** | Todos los @Service, @Controller, @Repository | Una instancia por clase (default Spring) |
| **Snapshot Pattern** | `SaleItem.unitCostAtSale` | Congelar valores en el momento de la transacción |

---

## 15. Glosario Rápido

| Término | Definición simplificada |
|---------|------------------------|
| **ORM** | Puente entre objetos Java y tablas SQL |
| **JPA** | Especificación de ORM para Java |
| **Hibernate** | Implementación de JPA que usa Spring Boot |
| **`@Transactional`** | "Todo o nada" — si falla, se deshace todo |
| **`@Version`** | Pestillo digital — evita pisar cambios de otro |
| **JWT** | Token firmado para autenticación sin estado |
| **FIFO** | "El primero que entra, primero que sale" |
| **Snapshot** | Valor congelado en el tiempo (para contabilidad) |
| **Boilerplate** | Código repetitivo que no aporta valor directo |
| **BigDecimal** | Número decimal EXACTO — para dinero |
| **Double** | Número decimal INEXACTO — NO para dinero |
| **CORS** | Mecanismo de seguridad para允许 orígenes cruzados |
| **Preflight** | Request OPTIONS de "permiso" antes del request real |
| **CORS Configuration Source** | Bean de filtro que corre antes que autenticación |
| **Refresh Token** | Token de larga duración para renovar access tokens |
| **`@Order(HIGHEST_PRECEDENCE)`** | Le dice a Spring "ejecutame primero" |
| **Race Condition** | Dos procesos modifican el mismo dato al mismo tiempo |
| **SRP** | Single Responsibility Principle — una clase, una responsabilidad |
| **Self-Demotion Guard** | Verificar que un ADMIN no se baje el rol a sí mismo |

---

## 📍 Estado Actual del Proyecto

### Completado ✅
- [x] Setup (Spring Boot + MySQL + Flyway + Docker + CI)
- [x] Módulo Productos (CRUD + soft delete)
- [x] Módulo Materia Prima (CRUD + conversionRatio)
- [x] Módulo Lotes de Producción (FIFO + @Version)
- [x] Módulo Ventas (SDD completo + FIFO real + tests)
- [x] CORS Configuration
- [x] Auth Refresh Token
- [x] Módulo Inventario (stock total + ajustes + integración)
- [x] Users Management (endpoints ADMIN + 3 stacked PRs)

### Pendiente ⏳
- [ ] SDD Verify — verificar que Users Management cumple specs
- [ ] SDD Archive — cerrar el cambio formalmente
- [ ] Próximo módulo: Reportes para dashboard

### Tests
- **Total**: ~84 tests (unitarios + controller + integración)
- **Stack**: JUnit 5 + Mockito + TestRestTemplate + H2
- **Cobertura**: JaCoCo (umbral actual 20%, temporal)

---

> **"No memorices código. Entendé los conceptos. El código cambia, los principios no."**
>
> — Vos, cuando seas Senior
