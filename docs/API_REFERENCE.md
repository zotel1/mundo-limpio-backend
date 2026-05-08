# API Reference - MundoLimpio Backend

> Backend para gestión de inventario y ventas de productos de limpieza.
> **Stack**: Java 21 + Spring Boot 3.3.0 + MySQL 8.0
> **Base URL**: `https://<railway-url>/api/v1`

---

## Table of Contents

1. [Authentication](#authentication)
2. [Products](#products)
3. [Bulk Products (Materia Prima)](#bulk-products-materia-prima)
4. [Production Batches](#production-batches)
5. [Sales](#sales)
6. [Error Handling](#error-handling)
7. [Business Rules](#business-rules)
8. [Security & Roles](#security--roles)

---

## Authentication

**Base path**: `/api/v1/auth`

### Register a new user

```
POST /api/v1/auth/register
```

**Request Body:**
```json
{
  "username": "operator1",
  "password": "securepassword"
}
```

**Response (201 Created):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "role": "OPERATOR",
  "username": "operator1",
  "createdAt": "2026-05-07T15:30:00Z"
}
```

**Notes:**
- All new users get `OPERATOR` role by default
- Password is hashed with BCrypt server-side

### Login

```
POST /api/v1/auth/login
```

**Request Body:**
```json
{
  "username": "operator1",
  "password": "securepassword"
}
```

**Response (200 OK):** Same as Register response above.

**Error (401 Unauthorized):** Invalid credentials.

### JWT Token Usage

For all protected endpoints, include the access token in the header:

```
Authorization: Bearer <accessToken>
```

**Token expiration:** The access token has a limited TTL. When it expires, you need to re-login (refresh token endpoint not yet implemented).

---

## Products

**Base path**: `/api/v1/products`

### Create Product

```
POST /api/v1/products
```

**Requires**: No authentication (public endpoint).

**Request Body:**
```json
{
  "sku": "DETERGENTE-500ML-001",
  "name": "Detergente Líquido 500ml",
  "minPrice": 1500.00
}
```

**Validation Rules:**
- `sku`: Required, only uppercase letters, numbers, and hyphens (e.g., `DETERGENTE-500ML-001`)
- `name`: Required, non-empty string
- `minPrice`: Required, must be positive (BigDecimal)

**Response (201 Created):**
```json
{
  "id": 1,
  "sku": "DETERGENTE-500ML-001",
  "name": "Detergente Líquido 500ml",
  "minPrice": 1500.00,
  "active": true
}
```

**Errors:**
- `400`: Validation failed (invalid SKU format, missing fields)
- `409`: SKU already exists

### Get Product by ID

```
GET /api/v1/products/{id}
```

**Response (200 OK):**
```json
{
  "id": 1,
  "sku": "DETERGENTE-500ML-001",
  "name": "Detergente Líquido 500ml",
  "minPrice": 1500.00,
  "active": true
}
```

**Errors:**
- `404`: Product not found

### Get Product by SKU

```
GET /api/v1/products/sku/{sku}
```

**Response (200 OK):** Same as Get by ID.

**Errors:**
- `404`: Product not found

### Get All Active Products

```
GET /api/v1/products
```

**Response (200 OK):**
```json
[
  {
    "id": 1,
    "sku": "DETERGENTE-500ML-001",
    "name": "Detergente Líquido 500ml",
    "minPrice": 1500.00,
    "active": true
  },
  {
    "id": 2,
    "sku": "LAVANDINA-3L-002",
    "name": "Lavandina 3 Litros",
    "minPrice": 2500.00,
    "active": true
  }
]
```

### Get All Products (including inactive)

```
GET /api/v1/products/all
```

**Response (200 OK):** Array of all products.

### Update Product

```
PUT /api/v1/products/{id}
```

**Requires**: ADMIN role.

**Request Body:** Same as Create.

**Response (200 OK):** Updated ProductResponse.

**Errors:**
- `400`: Validation failed
- `404`: Product not found
- `409`: New SKU already exists in another product

### Delete Product (Soft Delete)

```
DELETE /api/v1/products/{id}
```

**Response (204 No Content):**

**Notes:**
- This does NOT delete the record. It sets `active = false`.
- Preserves referential integrity with production batches and sales history.

### Reactivate Product

```
PATCH /api/v1/products/{id}/reactivate
```

**Response (204 No Content):**

**Notes:**
- Reverses soft delete by setting `active = true`.

---

## Bulk Products (Materia Prima)

**Base path**: `/api/v1/bulk-products`

**Requires**: ADMIN role for ALL endpoints.

### Create Bulk Product

```
POST /api/v1/bulk-products
```

**Request Body:**
```json
{
  "name": "Cloro Puro",
  "currentStockLiters": 100.00,
  "costperLiter": 500.00,
  "conversionRatio": 4.0
}
```

**Response (201 Created):**
```json
{
  "id": 1,
  "name": "Cloro Puro",
  "currentStockLiters": 100.00,
  "costPerLiter": 500.00,
  "conversionRatio": 4.0
}
```

**Notes:**
- `conversionRatio`: How many units of finished product you get per liter of raw material. Example: 4 means 1L of raw material produces 4 units of finished product.

### Get All Bulk Products

```
GET /api/v1/bulk-products
```

**Response (200 OK):** Array of BulkProductResponse.

### Get Bulk Product by ID

```
GET /api/v1/bulk-products/{id}
```

**Response (200 OK):** Single BulkProductResponse.

**Errors:**
- `404`: Bulk product not found

### Update Bulk Product

```
PUT /api/v1/bulk-products/{id}
```

**Request Body:** Same as Create.

**Response (200 OK):** Updated BulkProductResponse.

**Errors:**
- `400`: Validation failed
- `404`: Not found

### Delete Bulk Product

```
DELETE /api/v1/bulk-products/{id}
```

**Response (204 No Content):**

**Warning:** Should not be deleted if it has associated production batches.

---

## Production Batches

**Base path**: `/api/v1/production-batches`

**Requires**: ADMIN role for ALL endpoints.

### Create Production Batch

```
POST /api/v1/production-batches
```

**Request Body:**
```json
{
  "productId": 1,
  "bulkProductId": 1,
  "rawQuantityUsed": 20.0
}
```

**Response (201 Created):**
```json
{
  "id": 1,
  "productId": 1,
  "productName": "Detergente Líquido 500ml",
  "bulkProductId": 1,
  "bulkProductName": "Cloro Puro",
  "initialQuantity": 80.0,
  "currentStock": 80.0,
  "unitCostAtProduction": 125.00,
  "rawQuantityUsed": 20.0,
  "productionDate": "2026-05-07T15:30:00Z"
}
```

**Notes:**
- `initialQuantity` = `rawQuantityUsed` * `conversionRatio`
- The system deducts raw material stock from the bulk product automatically
- `unitCostAtProduction` is calculated based on raw material cost

**Errors:**
- `400`: Validation failed
- `404`: Product or BulkProduct not found

### Get Batches by Product ID

```
GET /api/v1/production-batches/product/{productId}
```

**Response (200 OK):** Array of ProductionBatchResponse.

### Get Batch by ID

```
GET /api/v1/production-batches/{id}
```

**Response (200 OK):** Single ProductionBatchResponse.

**Errors:**
- `404`: Batch not found

---

## Sales

**Base path**: `/api/v1/sales`

**Requires**: ADMIN role for POST. (Future GET endpoints may be accessible by OPERATOR.)

### Create Sale (FIFO Stock Deduction)

```
POST /api/v1/sales
```

**Requires**: ADMIN role.

**Request Body:**
```json
{
  "productId": 1,
  "quantity": 30.0
}
```

**Response (201 Created):**
```json
{
  "id": 1,
  "totalAmount": 45000.00,
  "createdAt": "2026-05-07T15:30:00",
  "items": [
    {
      "batchId": 1,
      "quantity": 20.0,
      "unitPrice": 1500.00,
      "unitCost": 125.00
    },
    {
      "batchId": 2,
      "quantity": 10.0,
      "unitPrice": 1500.00,
      "unitCost": 130.00
    }
  ]
}
```

**FIFO Logic:**
- Stock is deducted from the **oldest production batch first** (ordered by `productionDate` ascending)
- If one batch doesn't have enough stock, the system continues to the next oldest
- Each item in the `items` array represents a deduction from a specific batch
- `totalAmount` = sum of (quantity * minPrice) for each item
- `unitCost` in each item is a **snapshot** of the batch's cost at the time of sale

**Example:**
- Batch A (oldest): 20 units in stock
- Batch B: 30 units in stock
- Batch C (newest): 50 units in stock
- Request: 40 units
- Result: 20 from Batch A, 20 from Batch B, Batch C untouched

**Errors:**
- `400`: Validation failed OR insufficient stock (message: "Insufficient stock. Available: X, Requested: Y")
- `401`: No authentication token
- `403`: User is not ADMIN
- `409`: Concurrent sale conflict (optimistic locking)

---

## Error Handling

All errors follow this standard format:

```json
{
  "code": "PRODUCT_NOT_FOUND",
  "message": "Product with ID 999 not found",
  "timestamp": "2026-05-07T15:30:00",
  "path": "/api/v1/products/999"
}
```

### HTTP Status Codes Summary

| Code | Meaning | When |
|------|---------|------|
| 200 | OK | Successful GET/PUT |
| 201 | Created | Successful POST (resource created) |
| 204 | No Content | Successful DELETE/PATCH |
| 400 | Bad Request | Validation failed, insufficient stock |
| 401 | Unauthorized | No JWT token or invalid token |
| 403 | Forbidden | Insufficient role (not ADMIN) |
| 404 | Not Found | Resource doesn't exist |
| 409 | Conflict | SKU already exists, optimistic lock failure |

---

## Business Rules

### 1. FIFO Stock Deduction
When selling products, the system ALWAYS deducts from the oldest production batch first. This ensures products don't expire sitting in stock.

### 2. Stock Validation
Before creating a sale, the system validates that total available stock across ALL batches >= requested quantity. If not, the sale is rejected with a clear error message.

### 3. Atomic Transactions
Sales are processed in a single database transaction. If any step fails, the entire sale is rolled back.

### 4. Optimistic Locking
Production batches use `@Version` for optimistic locking. If two sales try to deduct from the same batch simultaneously, one will fail with a 409 Conflict.

### 5. Soft Deletes
Products are never hard-deleted. They are marked as `active = false`. This preserves referential integrity with production batches and sales history.

### 6. Conversion Ratio
When creating a production batch: `finishedQuantity = rawMaterialUsed × conversionRatio`

### 7. Price Snapshots
When a sale is created, the price and cost are **snapshotted** into the SaleItem. This means historical sales show what the price was at the time, even if the product's minPrice changes later.

---

## Security & Roles

### Role Hierarchy

| Role | Permissions |
|------|-------------|
| **ADMIN** | Full access: CRUD on all resources, create sales |
| **OPERATOR** | Created via registration, no ADMIN access to protected endpoints |

### Authentication Flow

1. User registers via `POST /api/v1/auth/register` (gets OPERATOR role)
2. User logs in via `POST /api/v1/auth/login` (gets JWT tokens)
3. User includes `Authorization: Bearer <token>` in all subsequent requests
4. Server validates token via `JwtAuthenticationFilter`

### Protected Endpoints

| Endpoint | ADMIN | OPERATOR | PUBLIC |
|----------|-------|----------|--------|
| POST /auth/register | ✅ | ✅ | ✅ |
| POST /auth/login | ✅ | ✅ | ✅ |
| GET /products/** | ✅ | ✅ | ✅ |
| POST /products | ✅ | ✅ | ✅ |
| PUT /products/** | ✅ | ❌ | ❌ |
| DELETE /products/** | ✅ | ❌ | ❌ |
| GET /bulk-products/** | ✅ | ❌ | ❌ |
| POST/PUT/DELETE /bulk-products/** | ✅ | ❌ | ❌ |
| GET /production-batches/** | ✅ | ❌ | ❌ |
| POST /production-batches | ✅ | ❌ | ❌ |
| POST /sales | ✅ | ❌ | ❌ |

---

## Deployment

- **Platform**: Railway.app
- **Database**: MySQL 8.0 (managed by Railway)
- **Docker**: Multi-stage build (Maven + Eclipse Temurin 21)
- **Healthcheck**: `/` endpoint (Spring Boot default)
- **Environment Variables**: Set via Railway dashboard
  - `SPRING_PROFILES_ACTIVE=prod`
  - `SPRING_DATASOURCE_URL` (auto-injected by Railway)
  - `SPRING_DATASOURCE_USERNAME` (auto-injected)
  - `SPRING_DATASOURCE_PASSWORD` (auto-injected)
  - `JWT_SECRET` (custom variable)

---

## Project Context

### Tech Stack
- Java 21 + Spring Boot 3.3.0
- Maven 3.9.12
- MySQL 8.0
- Spring Security with JWT (jjwt 0.12.5)
- Flyway for database migrations
- Lombok for boilerplate reduction
- JaCoCo for code coverage (70% minimum)

### Architecture
- **Layered by modules**: Each feature has its own package with domain, dto, mapper, service, controller, repository layers
- **DTOs**: Java Records (immutable by default)
- **Validation**: Jakarta Validation annotations
- **OpenAPI**: springdoc-openapi for API documentation

### Key Design Decisions
1. **Java Records over Classes**: All DTOs use records for immutability
2. **BigDecimal over Double**: All monetary and quantity values use BigDecimal for precision
3. **Soft Deletes**: Products are marked inactive, never hard-deleted
4. **Constructor Injection**: All services/controllers use constructor DI (no @Autowired)
5. **Spanish Comments**: All code comments are in Spanish for learning purposes
6. **FIFO over LIFO**: Stock deduction uses oldest-first strategy

### Testing
- Unit tests: JUnit 5 + Mockito
- Integration tests: Spring Boot Test + H2 (in-memory)
- Security tests: spring-security-test
- Coverage: JaCoCo (70% minimum)
