# Fix: SaleItem.quantity Integer → BigDecimal (R6-R8)

## Problem

`SaleItem.quantity` was declared as `Integer`, but `SaleRequest.quantity` and
`SaleItemResponse.quantity` were already `BigDecimal`. This type mismatch forced:

1. A `.intValue()` call in `SaleService.java` — **lost fractional precision**
   (e.g., `2.5` became `2`)
2. A `new BigDecimal(item.getQuantity())` wrapper in `SaleMapper.java` — unnecessary
   conversion that could mask the actual type mismatch

## Solution

### 1. SaleItem.java — Change field type and column annotation

```java
// Before:
@Column(nullable = false)
private Integer quantity;

// After:
@Column(nullable = false, precision = 19, scale = 4)
private BigDecimal quantity;
```

**`precision=19, scale=4`** matches the database column definition for other
BigDecimal fields in the schema. Scale=4 allows up to 4 decimal places.

### 2. SaleItem.java — Update constructor and getter

```java
// Before:
public SaleItem(Long productionBatchId, Integer quantity, ...)
public Integer getQuantity() { return quantity; }

// After:
public SaleItem(Long productionBatchId, BigDecimal quantity, ...)
public BigDecimal getQuantity() { return quantity; }
```

### 3. SaleService.java — Remove `.intValue()` call

```java
// Before (L152):
SaleItem item = new SaleItem(batch.getId(), quantityFromBatch.intValue(), ...);

// After:
SaleItem item = new SaleItem(batch.getId(), quantityFromBatch, ...);
```

`quantityFromBatch` is already `BigDecimal` (computed from
`remainingQuantity.min(batchStock)`). No conversion needed.

### 4. SaleMapper.java — Remove BigDecimal wrapper

```java
// Before (L69):
new java.math.BigDecimal(item.getQuantity())

// After:
item.getQuantity()
```

Since `getQuantity()` now returns `BigDecimal` directly, no wrapper needed.

## Why This Matters

Products can be sold in **fractions** (e.g., 2.5 liters of detergent). Using
`Integer` meant:

```
SaleRequest.quantity = 2.5  →  SaleItem.quantity = 2  ✗ (precision lost!)
```

This is a data corruption issue — the sale records would show 2 units instead of
the actual 2.5 units sold. Financial reports and inventory tracking would be wrong.

## Key Learnings

- **Don't mix types in a chain**: `SaleRequest` → `SaleService` → `SaleItem` →
  `SaleMapper` → `SaleItemResponse`. All should use the same type for `quantity`.
- **Always check the full data flow** when changing a type:
  - Input DTO (`SaleRequest.quantity`) → already `BigDecimal` ✅
  - Domain entity (`SaleItem.quantity`) → was `Integer` ❌
  - Output DTO (`SaleItemResponse.quantity`) → already `BigDecimal` ✅
  - DB column → needed `precision/scale` annotation ✅
- **`PurchaseItem` still uses `Integer`** — that's a different domain (raw materials
  are counted in whole units, not fractions).
