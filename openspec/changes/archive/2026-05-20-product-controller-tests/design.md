# Design: Product Controller Tests

## Technical Approach

Add 2 test files mirroring EXISTING project patterns exactly — **zero production code changes**. ProductControllerIT.java already provides 18 integration tests (HTTP → PostgreSQL via Testcontainers). The gap is unit-level isolation: service logic (Mockito) and mapper conversion (Spring component wiring).

## Architecture Decisions

| Option | Tradeoff | Decision |
|--------|----------|----------|
| ProductServiceTest: @SpringBootTest | Full context, slow, DB dependency | **MockitoExtension** — follows InventoryServiceTest pattern, isolates business logic from DB |
| ProductServiceTest: MockitoExtension | Fast, isolated, no Docker needed | ✅ CHOSEN. Matches InventoryServiceTest pattern EXACTLY. Tests only business logic branches. |
| ProductMapperTest: Mockito unit | Fast but skips @Component wiring verification | ❌ REJECTED. SaleMapperTest uses @SpringBootTest to verify @Component injection. |
| ProductMapperTest: extends AbstractIntegrationTest | Spring context + Testcontainers, verifies real wiring | ✅ CHOSEN. Matches SaleMapperTest pattern EXACTLY. ProductMapper is a @Component; verifying injection proves it's wired correctly. |
| ProductMapperTest: unit-only with `new ProductMapper()` | No Spring, fastest | ❌ REJECTED. Would break project convention — existing SaleMapperTest uses @Autowired. |

**Rationale**: Follow project conventions, don't invent patterns. Inventory module = Mockito services. Sales module = Spring-wired mappers. Product module = both.

## Data Flow

```
ProductServiceTest (Mockito):
  @Mock ProductRepository ──→ @InjectMocks ProductService ←── @Mock ProductMapper
       │                              │
       └── stubbed responses          └── verified interactions (verify/save/findById)

ProductMapperTest (Spring):
  AbstractIntegrationTest → PostgreSQL container → Flyway migrations → @Component ProductMapper
                                                       │
                                  ProductRequest/Product → toEntity()/toResponse() → ProductResponse/Product
```

## Test Case Matrix

### ProductServiceTest.java — 14 test methods

Package: `com.mundolimpio.application.product.service`
Pattern: `@ExtendWith(MockitoExtension.class)` + `@Mock ProductRepository` + `@Mock ProductMapper` + `@InjectMocks ProductService`
Assertions: JUnit 5 (`assertEquals`, `assertThrows`, `assertTrue`) + Mockito `verify()`

| # | Test Method | What It Verifies | Mock Setup | Expected |
|---|------------|-----------------|------------|----------|
| 1 | `createProduct_Success` | New SKU → ProductResponse with saved data | `existsBySku→false`, `toEntity→product`, `save→savedProduct`, `toResponse→response` | response matches, `save()` called |
| 2 | `createProduct_DuplicateSku_ThrowsAlreadyExists` | `existsBySku=true` → exception, no save | `existsBySku→true` | `ProductAlreadyExistsException`, `save()` never called |
| 3 | `getProductById_Found_ReturnsProduct` | `findById` returns product → mapped to response | `findById→Optional.of(product)`, `toResponse→response` | response.id matches, `findById(id)` called |
| 4 | `getProductById_NotFound_ThrowsException` | `findById` empty → `ProductNotFoundException` | `findById→Optional.empty()` | exception with "ID: 999" in message |
| 5 | `getProductBySku_Found_ReturnsProduct` | `findBySku` returns product → mapped to response | `findBySku→Optional.of(product)`, `toResponse→response` | response.sku matches |
| 6 | `getProductBySku_NotFound_ThrowsException` | `findBySku` empty → `ProductNotFoundException` | `findBySku→Optional.empty()` | exception with SKU in message |
| 7 | `getAllActiveProducts_ReturnsList` | `findByActiveTrue` → stream mapped via `toResponse` | `findByActiveTrue→[p1,p2]`, `toResponse(p1)→r1`, `toResponse(p2)→r2` | list size=2, mapper called 2× |
| 8 | `getAllProducts_ReturnsAll` | `findAll` → stream mapped via `toResponse` | `findAll→[p1,p2]`, `toResponse(p1)→r1`, `toResponse(p2)→r2` | list size=2 |
| 9 | `updateProduct_Success` | Changed SKU (no conflict), fields updated, saved | `findById→product`, `existsBySku→false`, `save→updated`, `toResponse→response` | product setters called, `save()` called, response has new SKU |
| 10 | `updateProduct_DuplicateSku_ThrowsException` | New SKU conflicts → `ProductAlreadyExistsException` | `findById→product`, `existsBySku(newSku)→true` | exception thrown, `save()` never called |
| 11 | `updateProduct_NotFound_ThrowsException` | Product doesn't exist → `ProductNotFoundException` | `findById→Optional.empty()` | exception thrown |
| 12 | `deleteProductSoftDelete_Success` | Product marked `active=false`, saved | `findById→product(active=true)` | `product.getActive()==false`, `save(product)` called |
| 13 | `deleteProductSoftDelete_NotFound_ThrowsException` | Product missing → `ProductNotFoundException` | `findById→Optional.empty()` | exception thrown |
| 14 | `reactivateProduct_Success` | Product marked `active=true`, saved | `findById→product(active=false)` | `product.getActive()==true`, `save(product)` called |
| 15 | `reactivateProduct_NotFound_ThrowsException` | Product missing → `ProductNotFoundException` | `findById→Optional.empty()` | exception thrown |
| 16 | `deleteProductPermanent_Success` | `deleteById(id)` called on repository | `findById→product` | `verify(repo).deleteById(id)`, no exception |
| 17 | `deleteProductPermanent_NotFound_ThrowsException` | Product missing → `ProductNotFoundException` | `findById→Optional.empty()` | exception thrown, `deleteById()` never called |

**Helper methods** (following InventoryServiceTest pattern):
```java
private Product createTestProduct(Long id, String sku, String name, BigDecimal price, boolean active)
private ProductRequest createTestRequest(String sku, String name, BigDecimal price)
private ProductResponse createExpectedResponse(Long id, String sku, String name, BigDecimal price, boolean active)
```

### ProductMapperTest.java — 4 test methods

Package: `com.mundolimpio.application.product.mapper`
Pattern: `extends AbstractIntegrationTest` + `@Autowired ProductMapper` (matches SaleMapperTest EXACTLY)
Assertions: AssertJ (`assertThat`)

| # | Test Method | What It Verifies | Input | Expected |
|---|------------|-----------------|-------|----------|
| 1 | `toEntity_MapsAllFields` | Request → Entity with all fields + `active=true` | `ProductRequest("SKU-1","Name",10.50)` | entity.sku="SKU-1", name="Name", minPrice=10.50, active=true |
| 2 | `toEntity_NullMinPrice_HandlesGracefully` | Null price doesn't crash mapper (no validation here) | `ProductRequest("SKU","Name",null)` | entity.minPrice=null, no NPE |
| 3 | `toResponse_MapsAllFields` | Entity → Response preserving all fields | `Product(1L,"SKU","Name",10.50,false)` | response.id=1, sku="SKU", name="Name", minPrice=10.50, active=false |
| 4 | `toResponse_ActiveTrue_PreservesFlag` | `active=true` maps correctly to response | `Product(2L,"SKU2","Name2",5.00,true)` | response.active=true |

## File Structure

```
src/test/java/com/mundolimpio/application/product/
├── controller/
│   └── ProductControllerIT.java       ← EXISTS (18 tests, no changes)
├── service/
│   └── ProductServiceTest.java         ← NEW (17 tests, MockitoExtension)
└── mapper/
    └── ProductMapperTest.java          ← NEW (4 tests, extends AbstractIntegrationTest)
```

**No production code changes.** `src/main/` untouched.

## Dependencies

All dependencies already in project `pom.xml`:
- `org.mockito:mockito-junit-jupiter` — `@ExtendWith(MockitoExtension.class)`, `@Mock`, `@InjectMocks`
- `org.assertj:assertj-core` — `assertThat(...).isNotNull()` for MapperTest
- `org.junit.jupiter:junit-jupiter` — `@Test`, `assertEquals`, `assertThrows`
- `org.testcontainers:postgresql` — PostgreSQL container (via AbstractIntegrationTest)
- `org.springframework.boot:spring-boot-starter-test` — `@SpringBootTest`, `@Autowired`

## Expected Coverage

| File | Method Coverage Target | Rationale |
|------|----------------------|-----------|
| ProductServiceTest | 100% of ProductService methods (8 of 9* public methods) | All business logic paths: create, read×4, update, delete×3. *`deleteProductPermanent` tested but rarely used in production. |
| ProductMapperTest | 100% of ProductMapper methods (2/2) | Both `toEntity()` and `toResponse()` verified with field-level assertions. |
| ProductControllerIT | Already covers 100% of controller endpoints | 8 endpoints × happy + error paths = 18 existing tests. |

## Migration / Rollout

No migration required. Tests only. Rollback: delete the 2 new files. Zero production impact.

## Open Questions

None. All patterns are verbatim copies from existing project conventions (InventoryServiceTest, SaleMapperTest). All dependencies are already in pom.xml.
