# Sales Module Specification

## Purpose

Módulo para registrar ventas de productos terminados, aplicando lógica **FIFO (First In, First Out)** para descontar stock de lotes de producción, con control de stock disponible y transacciones atómicas.

## Requirements

### Requirement: Create Sale (Registrar Venta)

The system **MUST** allow registering a new sale by providing product ID and quantity.  
The system **MUST** apply FIFO logic, deducting stock from the oldest production batch first.  
The system **MUST** create a `Sale` record and corresponding `SaleItem` records in a single atomic transaction.  
The system **MUST** return HTTP 201 Created with sale details upon success.

#### Scenario: Happy Path - Successful Sale with FIFO

- **GIVEN** Product with ID 1 exists and has active production batches with available stock
- **GIVEN** Production batches are ordered by productionDate ascending (oldest first)
- **GIVEN** Total available stock for product ID 1 is 100 units (across all batches)
- **WHEN** POST `/api/v1/sales` with body `{ "productId": 1, "quantity": 30 }`
- **THEN** System creates a `Sale` record with `totalAmount` calculated
- **AND** System creates `SaleItem` records deducting stock from oldest batches first
- **AND** System updates `currentStock` in affected `ProductionBatch` records
- **AND** System returns HTTP 201 Created with sale response

#### Scenario: FIFO Deduction Order

- **GIVEN** Product with ID 1 has 3 production batches:
  - Batch A: 20 units in stock, produced 2026-01-01
  - Batch B: 30 units in stock, produced 2026-02-01
  - Batch C: 50 units in stock, produced 2026-03-01
- **WHEN** POST `/api/v1/sales` with body `{ "productId": 1, "quantity": 40 }`
- **THEN** System deducts 20 units from Batch A (now 0)
- **AND** System deducts 20 units from Batch B (now 10 remaining)
- **AND** System does NOT touch Batch C (newest)
- **AND** System creates appropriate `SaleItem` records for Batch A and B

### Requirement: Validate Stock Availability

The system **MUST** validate that requested quantity does not exceed total available stock before processing the sale.  
The system **SHOULD** return a clear error message indicating available stock vs. requested quantity.

#### Scenario: Insufficient Stock Error

- **GIVEN** Product with ID 1 has total available stock of 50 units across all batches
- **WHEN** POST `/api/v1/sales` with body `{ "productId": 1, "quantity": 100 }`
- **THEN** System does NOT create any `Sale` or `SaleItem` records
- **AND** System does NOT modify any `ProductionBatch` stock
- **AND** System returns HTTP 400 Bad Request
- **AND** Response body contains error message: "Insufficient stock. Available: 50, Requested: 100"

### Requirement: Access Control for Sales

The system **MUST** restrict POST `/api/v1/sales` to users with role **ADMIN** only.  
The system **MUST** return HTTP 403 Forbidden if a non-ADMIN user attempts to create a sale.

#### Scenario: Unauthorized Access (Non-ADMIN)

- **GIVEN** User is authenticated with role **OPERATOR** (not ADMIN)
- **WHEN** POST `/api/v1/sales` with valid sale request body
- **THEN** System returns HTTP 403 Forbidden
- **AND** No sale is created

#### Scenario: Unauthenticated Access

- **GIVEN** No JWT token is provided (or invalid token)
- **WHEN** POST `/api/v1/sales` with valid sale request body
- **THEN** System returns HTTP 401 Unauthorized

### Requirement: Handle Concurrent Sales (Race Conditions)

The system **MUST** use optimistic locking (`@Version` on `ProductionBatch`) to prevent race conditions when multiple sales deduct from the same batch simultaneously.  
The system **SHOULD** throw `OptimisticLockException` if a conflict is detected, allowing the caller to retry.

#### Scenario: Concurrent Deduction Race Condition

- **GIVEN** Production batch with ID 1 has `currentStock = 10` and `version = 5`
- **WHEN** Two simultaneous sales attempt to deduct 5 units each from batch ID 1
- **THEN** One transaction succeeds (batch now has `currentStock = 5`, `version = 6`)
- **AND** The other transaction fails with `OptimisticLockException` due to version mismatch
- **AND** Failed transaction triggers rollback (no partial deduction)

### Requirement: Input Validation

The system **MUST** validate request body fields using Jakarta Validation.  
The system **MUST** return HTTP 400 Bad Request for invalid inputs.

#### Scenario: Invalid Input - Missing Fields

- **GIVEN** Request body is missing `productId` or `quantity`
- **WHEN** POST `/api/v1/sales` with incomplete body `{ "productId": 1 }` (missing quantity)
- **THEN** System returns HTTP 400 Bad Request
- **AND** Response contains validation error details

#### Scenario: Invalid Input - Negative Quantity

- **GIVEN** Request body has `quantity: -5` (negative value)
- **WHEN** POST `/api/v1/sales` with `{ "productId": 1, "quantity": -5 }`
- **THEN** System returns HTTP 400 Bad Request
- **AND** Response indicates quantity must be positive
