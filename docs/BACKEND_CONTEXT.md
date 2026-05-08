# Backend Context - MundoLimpio

> Este documento contiene el contexto completo del backend para que cualquier agente del frontend entienda cómo funciona el sistema.

---

## Project Overview

MundoLimpio is an **inventory management and sales system** for cleaning products. It tracks:
1. **Products** (finished goods like detergents, bleach)
2. **Bulk Products** (raw materials like concentrated chlorine)
3. **Production Batches** (when raw material is converted to finished products)
4. **Sales** (selling finished products with FIFO stock deduction)

## Architecture

```
mundo-limpio-backend/
├── src/main/java/com/mundolimpio/
│   ├── MundoLimpioApplication.java          # Spring Boot entry point
│   └── application/
│       ├── product/                         # Product catalog management
│       │   ├── domain/                      # JPA entities
│       │   ├── dto/                         # Request/Response records
│       │   ├── mapper/                      # Entity ↔ DTO converters
│       │   ├── service/                     # Business logic
│       │   ├── controller/                  # REST endpoints
│       │   └── repository/                  # Spring Data JPA repositories
│       ├── bulkproduct/                     # Raw material management
│       ├── productionbatch/                 # Production batch management
│       ├── sales/                           # Sales with FIFO logic
│       ├── user/                            # User management & auth
│       ├── security/                        # JWT & Spring Security config
│       └── common/                          # Shared utilities & error handling
├── src/main/resources/
│   ├── db/migration/                        # Flyway migrations
│   └── application.properties              # Spring Boot config
└── docker-compose.yml                       # Local dev with MySQL
```

## Critical Implementation Details

### 1. Authentication Flow
- JWT tokens generated on login/register
- `JwtAuthenticationFilter` intercepts all requests, validates token
- **BUG FIXED**: Original filter had `username == null` instead of `!= null` — auth never worked
- Spring Security returns 403 by default for unauthenticated; we added `HttpStatusEntryPoint(UNAUTHORIZED)` to return proper 401

### 2. Sales Module - FIFO Logic
The most complex feature. When creating a sale:
1. Check total available stock across ALL batches for the product
2. If insufficient → return 400 with clear error message
3. Iterate batches ordered by `productionDate ASC` (oldest first)
4. Deduct stock from each batch until quantity is fulfilled
5. Create Sale + SaleItem records in a single `@Transactional`
6. Snapshot price and cost at time of sale

### 3. Concurrency Handling
- `@Version` annotation on `ProductionBatch` and `Sale` entities
- Uses optimistic locking: if two sales hit the same batch simultaneously, one fails
- `GlobalExceptionHandler` catches `OptimisticLockingFailureException` → returns 409

### 4. Database Migrations
- Flyway manages schema evolution
- 3 migrations: Initial schema, conversion ratio, production batch table
- H2 used for tests, MySQL for production

### 5. DTO Patterns
- All DTOs are Java Records (immutable)
- Request DTOs have Jakarta Validation annotations
- Response DTOs are clean (no validation)
- Mappers are `@Component` beans for easy injection

### 6. Error Handling
- `GlobalExceptionHandler` centralizes all error responses
- Returns consistent `ErrorResponse` format with code, message, timestamp, path
- Handles: IllegalArgumentException, ResourceNotFoundException, AccessDeniedException, OptimisticLockingFailureException, MethodArgumentNotValidException

### 7. Security Rules
- `/api/v1/auth/**` — Public (register, login)
- `/api/v1/products/**` — Partially public (GET is public, POST/PUT/DELETE need ADMIN)
- `/api/v1/bulk-products/**` — ADMIN only
- `/api/v1/production-batches/**` — ADMIN only
- `/api/v1/sales/**` — ADMIN only (POST)

### 8. Key Classes for Frontend Integration

| Class | Purpose | Frontend Relevance |
|-------|---------|-------------------|
| `SaleController` | POST /api/v1/sales | Create sales from mobile app |
| `ProductController` | CRUD /api/v1/products | Product catalog display |
| `AuthController` | /api/v1/auth/login, /register | User authentication |
| `ProductionBatchController` | GET /api/v1/production-batches | Stock display |
| `BulkProductController` | CRUD /api/v1/bulk-products | Raw material management |

### 9. Deployment
- Railway.app (Docker-based)
- MySQL managed by Railway
- Environment variables auto-injected by Railway
- Healthcheck: `/` endpoint

## Future Features (not yet implemented)
- Refresh token endpoint
- GET endpoints for sales (history, filtering)
- Dashboard/analytics endpoints
- OPERATOR role access to view-only endpoints
- Pagination and search for list endpoints
- File upload for product images
