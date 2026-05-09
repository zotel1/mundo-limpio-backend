# Mundo Limpio Backend

Sistema de inventario para productos de limpieza con gestión de materia prima y lotes de producción.

## Tecnologías

- **Java 21** + **Spring Boot 3.3.0**
- **Maven** (build tool)
- **MySQL** (producción) + **H2** (tests)
- **Flyway** (migraciones de base de datos)
- **JWT** (autenticación)
- **Spring Security CORS** (Cross-Origin Resource Sharing para app Flutter)
- **OpenAPI/Swagger** (documentación de endpoints)
- **Lombok** (reducción de boilerplate)

## Arquitectura

Proyecto estructurado por módulos funcionales:

```
src/main/java/com/mundolimpio/application/
├── product/           # Gestión de productos terminados
├── bulkproduct/       # Gestión de materia prima
├── productionbatch/    # Lotes de producción (FIFO)
├── sales/             # Ventas (FIFO, FIFO discount)
├── user/              # Usuarios y autenticación
├── security/          # Configuración JWT y Spring Security
└── common/            # Excepciones y DTOs compartidos
```

Cada módulo sigue el patrón **Controller → Service → Repository → Domain**.

## Endpoints

### Autenticación (`/api/v1/auth`)
| Método | Endpoint | Descripción | Rol |
|--------|----------|-------------|-----|
| POST | `/register` | Registrar usuario | Público |
| POST | `/login` | Obtener JWT (access + refresh) | Público |
| POST | `/refresh` | Renovar access token con refresh token | Público (con token válido) |

### Productos (`/api/v1/products`)
| Método | Endpoint | Descripción | Rol |
|--------|----------|-------------|-----|
| POST | `/` | Crear producto | ADMIN |
| GET | `/{id}` | Obtener por ID | ADMIN |
| GET | `/sku/{sku}` | Obtener por SKU | ADMIN |
| GET | `/` | Listar activos | ADMIN |
| GET | `/all` | Listar todos | ADMIN |
| PUT | `/{id}` | Actualizar | ADMIN |
| DELETE | `/{id}` | Soft delete | ADMIN |
| PATCH | `/{id}/reactivate` | Reactivar | ADMIN |

### Materia Prima (`/api/v1/bulk-products`)
| Método | Endpoint | Descripción | Rol |
|--------|----------|-------------|-----|
| POST | `/` | Crear materia prima | ADMIN |
| GET | `/` | Listar todas | ADMIN |
| GET | `/{id}` | Obtener por ID | ADMIN |
| PUT | `/{id}` | Actualizar | ADMIN |
| DELETE | `/{id}` | Eliminar | ADMIN |

### Lotes de Producción (`/api/v1/production-batches`)
| Método | Endpoint | Descripción | Rol |
|--------|----------|-------------|-----|
| POST | `/` | Crear lote (producción) | ADMIN |
| GET | `/product/{productId}` | Lotes por producto | ADMIN |
| GET | `/{id}` | Obtener por ID | ADMIN |

### Ventas (`/api/v1/sales`)
| Método | Endpoint | Descripción | Rol |
|--------|----------|-------------|-----|
| POST | `/` | Crear venta (FIFO) | ADMIN |

**Lógica FIFO**: Al crear una venta, el sistema descuenta stock del lote de producción más antiguo primero. Si un lote no tiene suficiente stock, complementa con el siguiente lote más viejo.

**Request ejemplo**:
```json
{
  "productId": 1,
  "quantity": 5
}
```

**Response ejemplo**:
```json
{
  "id": 1,
  "totalAmount": 27.50,
  "createdAt": "2026-05-07T23:00:00",
  "items": [
    {
      "batchId": 3,
      "quantity": 5.00,
      "unitPrice": 5.50,
      "unitCost": 5.50
    }
  ]
}
```

## Ejecución

### Requisitos
- Java 21
- Maven 3.9+
- MySQL 8+ (o usar Docker)

### Local
```bash
# Clonar repo
git clone https://github.com/zotel1/mundo-limpio-backend.git
cd mundo-limpio-backend

# Ejecutar con Maven
./mvnw spring-boot:run

# O compilar y ejecutar
./mvnw clean install
java -jar target/mundolimpio-0.0.1-SNAPSHOT.jar
```

### Con Docker
```bash
docker-compose up -d
```

## CORS Configuration

La aplicación tiene CORS habilitado para permitir que la app Flutter mobile se comunique con la API desde orígenes cruzados.

### Orígenes permitidos
- `http://localhost:8080` — iOS simulator y desarrollo local
- `http://10.0.2.2:8080` — Android emulator

### Cómo funciona
- `CorsConfig.java` define un bean `CorsConfigurationSource` con la configuración CORS
- `SecurityConfig.java` lo inyecta via `.cors()` en la SecurityFilterChain, ANTES de la autenticación
- Los orígenes se configuran en `application.yml` (`application.cors.allowed-origins`) y se pueden overridear con variable de entorno `APPLICATION_CORS_ALLOWED_ORIGINS`
- Usa JWT Bearer token (no cookies), por lo que `allowCredentials=false`

### Tests

```bash
# Ejecutar tests (usa H2 in-memory)
./mvnw test

# Con cobertura
./mvnw test jacoco:report
```

Tests existentes:
- `ProductControllerIT` - Integración de productos
- `BulkProductControllerIT` - Integración de materia prima
- `ProductionBatchControllerIT` - Integración de lotes
- `ProductionBatchServiceTest` - Unitario de servicio
- `SaleControllerIT` - 8 integration tests (security + FIFO + stock)
- `SaleServiceTest` - 4 tests de servicio
- `SaleMapperTest` - 2 tests de mapper
- `CorsConfigTest` - Verificación del bean CorsConfigurationSource
- `CorsSecurityTest` - 3 tests de integración CORS (preflight, origen no permitido, GET con Origin)

## Documentación API

Una vez ejecutado, accedé a Swagger UI:
```
http://localhost:8080/swagger-ui.html
```

## Workflow de Desarrollo

1. Crear branch desde `develop`: `git checkout -b feature/nombre-modulo`
2. Hacer commits siguiendo [Conventional Commits](https://www.conventionalcommits.org/)
3. PR a `develop` → revisión y merge
4. PR de `develop` a `main` → producción
5. El agente de IA (Gentle AI) hace commits, vos hacés los PRs

## Estructura de Base de Datos

Migraciones en `src/main/resources/db/migration/` (Flyway):
- `V1__Initial_Schema.sql` - Tablas iniciales

## Siguientes Pasos

- [x] ~~Módulo de Ventas (FIFO para descuento de stock)~~ — **COMPLETADO**
- [ ] **Auth Refresh Token** — Phase 2-4 (Core Implementation + Testing + Wiring)
- [ ] Unit tests para todos los services (ProductService, BulkProductService)
- [ ] Reportes de inventario y producción
- [ ] Gestión de roles y permisos más granular
- [ ] Pagination en endpoints de lista
- [ ] CI/CD con GitHub Actions (configurado pero pendiente afinar)

## Memoria del Proyecto

Este proyecto usa **Engram** para memoria persistente y **SDD (Spec-Driven Development)** para el flujo de desarrollo.

Al clonar en otra PC, asegurate de:
1. Tener Engram configurado
2. Tener las skills de SDD instaladas
3. El contexto persiste automáticamente

---

**Desarrollado con 🧼 para Mundo Limpio**
