# Fix: Migrate Deprecated User Constructor in Tests (R10)

## Problem

The 3-parameter constructor `User(String username, String password, Role role)`
was annotated `@Deprecated` after migrating to a multi-role model. However, **13
usages** across **9 test files** still used it, producing compiler warnings and
technical debt.

The deprecated constructor auto-generated an email as `username + "@mundolimpio.com"`,
which is incorrect for test clarity.

## Solution

### Pattern

Every occurrence of:

```java
new User("username", "password", Role.X)
```

Was replaced with:

```java
new User("username", "username@mundolimpio.com", "password", Role.X)
```

### Complete List of Migrations

| File | Username | Email |
|------|----------|-------|
| `SaleControllerIT` (2) | admin_sales, operator_sales | `admin_sales@mundolimpio.com` |
| `ReceiptControllerIT` (2) | admin_test, operator_test | `admin_test@mundolimpio.com` |
| `ReceiptConfirmE2EIT` (1) | admin_e2e | `admin_e2e@mundolimpio.com` |
| `PurchaseRepositoryTest` (1) | admin_repo | `admin_repo@mundolimpio.com` |
| `PurchaseTest` (3) | admin_test, admin (x2) | `admin_test@mundolimpio.com`, `admin@mundolimpio.com` |
| `ReceiptMapperTest` (1) | admin | `admin@mundolimpio.com` |
| `ReceiptConfirmationServiceTest` (1) | admin_test | `admin_test@mundolimpio.com` |
| `BulkProductControllerIT` (1) | admin_test | `admin_test@mundolimpio.com` |
| `ProductionBatchControllerIT` (1) | admin_prod | `admin_prod@mundolimpio.com` |

### Why Descriptive Emails?

The old deprecated constructor auto-generated emails like `admin_test@mundolimpio.com`
anyway (via `this(username, username + "@mundolimpio.com", password, role)`).
By making the email explicit, we:

1. **Remove compiler warnings** — no more `@Deprecated` on every new User() call
2. **Improve readability** — email is explicit in the test setup
3. **Enable different emails** — in production, users register with their real email,
   not an auto-generated one. Tests should reflect this reality.

## Key Learnings

- **Use `grep` to find all deprecated usages** — the regex
  `new User("[^"]+",\s*"[^"]+",\s*Role\.` catches all 3-param constructor calls
  without matching the 4-param version.
- **Each username gets its own email** — don't use a single email for all users
  because `User.email` is `unique = true` in the schema. Descriptive emails also
  help identify which test created the user when debugging.
- **The deprecated constructor still exists** — it's still useful for quick setup
  in throwaway code, but production tests should always use the explicit
  4-param constructor.
