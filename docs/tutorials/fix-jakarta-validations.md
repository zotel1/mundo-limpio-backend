# Fix: Jakarta Validation Annotations (R4-R5)

## Problem

`RegisterRequest` and `BulkProductRequest` had **no Jakarta validation annotations**.
This meant:

- Any string could be passed as email (even `""` or `"not-an-email"`)
- Any string could be passed as password (even `"12"` — 2 chars)
- Bulk products could be created with negative costs or stock values
- The API would return HTTP 500 or unexpected behavior instead of a clear 400

## Solution

### RegisterRequest

```java
public record RegisterRequest(
    @NotBlank @Email @Size(min = 5, max = 100) String email,
    @NotBlank @Size(min = 6, max = 100) String password
) {}
```

### BulkProductRequest

```java
public record BulkProductRequest(
    @NotBlank String name,
    @NotNull @PositiveOrZero BigDecimal currentStockLiters,
    @NotNull @Positive BigDecimal costperLiter,
    @NotNull @Positive BigDecimal conversionRatio
) {}
```

## Annotations Explained

| Annotation | What it does | Why that value |
|------------|-------------|----------------|
| `@NotBlank` | Rejects null, empty, and blank strings | Prevents empty names/emails |
| `@Email` | Validates email format | Must contain `@` and valid domain |
| `@Size(min=5, max=100)` | String length constraints | email: at least 5 chars (a@b.c), max 100 |
| `@Size(min=6, max=100)` | String length constraints | password: at least 6 chars minimum |
| `@Positive` | Value > 0 | Cost and ratio must be positive numbers |
| `@PositiveOrZero` | Value ≥ 0 | Stock can be zero (out of stock) |
| `@NotNull` | Rejects null | Required for BigDecimal fields |

## Why No New Tests?

The controllers already use `@Valid` on their `@RequestBody` parameters:
- `AuthController.register(@Valid @RequestBody RegisterRequest request)`
- `BulkProductController.create(@Valid @RequestBody BulkProductRequest request)`

Spring automatically maps `MethodArgumentNotValidException` to HTTP 400 with a
descriptive error message. The existing IT tests (which create valid data) continue
to pass because they only send valid requests.

## Key Learnings

- **`@Valid` in the controller + annotations on the DTO = automatic validation**.
  No manual validation code needed.
- **`@Positive` vs `@PositiveOrZero`**: Know your domain. Stock CAN be zero (empty),
  but cost per liter CANNOT be zero (you never get anything for free).
- **Existing ITs don't break** because they use valid data. The validation only
  affects invalid input.
