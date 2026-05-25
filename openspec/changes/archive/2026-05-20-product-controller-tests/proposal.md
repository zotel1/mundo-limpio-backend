# Proposal: Product Controller Tests

## Intent

Close the critical test gap in the Product module (0% → 80%+). No production code changes — pure test coverage to enable safe refactoring.

## Scope

### In Scope
- **ProductServiceTest.java** — unit tests with Mockito (~13-15 methods)
- **ProductControllerIT.java** — @SpringBootTest + Testcontainers integration tests (~12 methods)
- **ProductMapperTest.java** — mapper unit tests (~4-5 methods)

### Out of Scope
- BulkProduct tests (separate change)
- Production code changes of any kind
- Integration test for ProductRepository (covered by controller IT)
- Security/permission tests (covered by SecurityConfig tests)

## Capabilities

### New Capabilities
- None — tests only, no new behavior

### Modified Capabilities
- None — no spec-level behavior changes

## Approach

Write 3 test files following exact patterns from InventoryControllerTest, InventoryServiceTest, and SaleServiceTest. Use Mockito for service/mapper unit tests, @SpringBootTest + Testcontainers PostgreSQL for controller IT. Strict TDD: write failing test, confirm red, implement mock/assertion, confirm green. ~25-30 test methods total.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `src/main/java/.../product/` | None | No production code changes |
| `src/test/java/.../product/ProductServiceTest.java` | New | Service unit tests (Mockito) |
| `src/test/java/.../product/ProductControllerIT.java` | New | Controller IT (Testcontainers) |
| `src/test/java/.../product/ProductMapperTest.java` | New | Mapper unit tests |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Testcontainers schema mismatch | Low | Copy proven config from InventoryControllerTest |
| SKU uniqueness mock differs from DB | Low | Verify constraint behavior before test |

## Rollback Plan

Delete the 3 new test files. Zero production impact.

## Dependencies

- Testcontainers PostgreSQL available locally
- Existing test patterns in inventory, sales, receipt modules

## Success Criteria

- [ ] All ~30 new tests pass
- [ ] No regression in existing test suite (142+ tests)
- [ ] Product module >80% method coverage
