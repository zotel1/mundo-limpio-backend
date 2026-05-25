# Fix: CUSTOMER Role and Registration (R1-R3)

## Problem

After a database reset, new users had **no roles assigned** during registration.
This caused two issues:

1. `AuthService.register()` created users with `Set.of()` (empty roles)
2. `buildLoginResponse()` returned `null` in the deprecated `role` field when roles
   were empty — the frontend received `"role": null` instead of a valid string
3. The `Role` enum had no `CUSTOMER` entry — no base role for read-only access

## Solution

### 1. Add CUSTOMER to Role.java

Insert `CUSTOMER` as the **first** enum value (before `ADMIN`). Semantic ordering:
the most restricted role comes first.

```java
public enum Role {
    CUSTOMER,    // ← new: base role, read-only access to products
    ADMIN,
    STOCK_MANAGER,
    // ...
}
```

**Why first?** Enum ordinal is sometimes used in DB mappings. `CUSTOMER` should be
ordinal 0 because it's the most restricted. Also, `Enum.toString()` output will
show `CUSTOMER` first — useful for debugging.

### 2. Assign CUSTOMER in AuthService.register()

Old code (no roles):
```java
User user = new User(username, request.email(),
        passwordEncoder.encode(request.password()));
```

New code:
```java
User user = new User(username, request.email(),
        passwordEncoder.encode(request.password()), Role.CUSTOMER);
```

**Why CUSTOMER and not another role?** Principle of least privilege. A newly
registered user should only be able to read products (GET endpoints are
`permitAll()`). If they need more, an ADMIN assigns higher roles via
`PATCH /users/{id}/roles`.

### 3. Fix buildLoginResponse() null safety

Old code:
```java
String deprecatedRole = user.getRole() != null ? user.getRole().name() : null;
```

New code:
```java
String deprecatedRole = userRoles.isEmpty() ? "CUSTOMER" : user.getRole().name();
```

**Why this change?** The deprecated `role` field should **never** be null.
Even if roles are somehow empty (defense in depth), we fallback to `"CUSTOMER"`.
The frontend always receives a non-null string.

## Files Changed

| File | Change |
|------|--------|
| `Role.java` | Add `CUSTOMER` first in enum |
| `AuthService.java` | Assign `Role.CUSTOMER` in register; fix null safety in buildLoginResponse |
| `AuthServiceTest.java` | Update assertions: `assertNull` → `assertEquals("CUSTOMER")` |
| `AuthControllerIT.java` | **NEW** — Integration tests for register/login returning CUSTOMER |

## Key Learnings

- **The `getRole()` method is `@Deprecated`** — it returns the first role's
  iterator value. When roles is empty, it returns `null`. Always prefer
  `getRoles()` for the full Set.
- **TDD worked well here**: writing the test first (`red` → expected CUSTOMER)
  confirmed the production code was wrong. Then `green` was just adding the
  right parameters.
- **Infrastructure note**: `AuthControllerIT` uses Testcontainers (needs Docker).
  Without Docker, the ITs are blocked but the unit tests (Mockito) still pass.
