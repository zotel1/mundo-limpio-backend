# Engram Memory Export - MundoLimpio Backend

> Exported: 2026-05-08
> Project: mundo-limpio-backend
> Source: Engram persistent memory system

---

## Project Context (from sdd-init)

**Tech Stack**: Java 21 + Spring Boot 3.3.0 + Maven 3.9.12
**Architecture**: Layered Architecture by modules (product, bulkproduct, productionbatch, user, security, common)
**Testing**: JUnit 5 + Mockito + AssertJ (spring-boot-starter-test), Spring Boot Test (integration), Testcontainers (CI)
**Style**: Lombok for boilerplate reduction, Jakarta Validation for DTOs
**CI/CD**: GitHub Actions with Maven build + test, Testcontainers for integration tests
**Database**: MySQL (production) with Flyway migrations, H2 (tests)
**Security**: JWT authentication (jjwt 0.12.5), Spring Security, RBAC (ADMIN/OPERATOR roles)
**Documentation**: OpenAPI/Swagger (springdoc-openapi-starter-webmvc-ui 2.0.2)
**Project Structure**: Each module has domain, repository, service, controller, dto, mapper
**Strict TDD**: enabled ✅ (test runner detected)
**Conventions**: Conventional Commits, branch naming with type/description
**Workflow**: feature branches → PR to develop → PR develop to main

---

## Architecture Overview (from session summary)

**Stack**: Spring Boot 3.x, Java 21, JPA/Hibernate, Spring Security + JWT, MySQL (prod), H2 (test), Flyway (migrations)
**Architecture**: Layered (application → domain → repository), NOT hexagonal/clean
**Build**: Maven (mvnw)

### Modules implemented:
1. **Auth** — JWT authentication, ADMIN/OPERATOR roles
2. **Product** — Finished products (CRUD, public endpoints)
3. **BulkProduct** — Raw materials (CRUD, ADMIN only)
4. **ProductionBatch** — Production lots linking Product + BulkProduct (CRUD, ADMIN only)
5. **Sales** — Sales with FIFO stock deduction (COMPLETE)

### Key architectural patterns:
- `@Version` on Sale, ProductionBatch, BulkProduct for optimistic locking
- Java Records for DTOs (immutable)
- Constructor injection (no @Autowired on fields)
- Jakarta Validation on DTOs
- GlobalExceptionHandler for centralized error handling
- Integration tests with H2 + TestRestTemplate + JWT tokens

### Git workflow:
- main → production, develop → integration, feature/* → work in progress
- Atomic commits per phase
- Conventional commits

---

## Critical Discoveries & Bug Fixes

### 1. JwtAuthenticationFilter Bug
**What**: Filter had `username == null` instead of `!= null`
**Impact**: Authentication NEVER worked — all requests were rejected
**Fix**: Changed condition, added `HttpStatusEntryPoint(UNAUTHORIZED)` for proper 401 responses
**Lesson**: Always test auth flow before building on top of it

### 2. Spring Security 403 vs 401
**What**: Spring Security returns 403 by default for unauthenticated requests
**Fix**: Added `HttpStatusEntryPoint(UNAUTHORIZED)` to SecurityConfig
**Result**: Proper 401 for unauthenticated, 403 for authenticated but insufficient role

### 3. GlobalExceptionHandler Requirements
**What**: Needed handlers for:
- `IllegalArgumentException` → 400 Bad Request
- `AccessDeniedException` → 403 Forbidden
- `OptimisticLockingFailureException` → 409 Conflict
- `MethodArgumentNotValidException` → 400 with field errors
**Lesson**: Centralized error handling is critical for consistent API responses

### 4. ProductionBatch File Location
**What**: Found ProductionBatch.java in WRONG place - it's in `domain/` subfolder!
**Path**: `src/main/java/com/mundolimpio/application/productionbatch/domain/ProductionBatch.java`
**Lesson**: Be careful with subfolder structure when searching

### 5. SaleService Method Bug
**What**: `calculateTotalAmount()` had `batch.getSalePrice()` instead of `batch.getUnitCostAtProduction()`
**Fix**: Changed to correct method name
**Lesson**: Method names matter — always verify what methods actually exist

---

## Design Decisions

### 1. FIFO over LIFO
**Choice**: Stock deduction uses oldest-first strategy
**Rationale**: Prevents products from expiring in stock, matches real-world business needs

### 2. Java Records over Classes
**Choice**: All DTOs use Java Records
**Rationale**: Immutability by default, less boilerplate, clear intent

### 3. BigDecimal over Double
**Choice**: All monetary and quantity values use BigDecimal
**Rationale**: Precision — avoids floating-point arithmetic issues

### 4. Soft Deletes
**Choice**: Products are marked inactive, never hard-deleted
**Rationale**: Preserves referential integrity with production batches and sales history

### 5. Constructor Injection
**Choice**: All services/controllers use constructor DI (no @Autowired on fields)
**Rationale**: Testability, explicit dependencies, immutability

### 6. Spanish Comments
**Choice**: All code comments are in Spanish
**Rationale**: User wants to understand the code later — comments explain WHAT and WHY

### 7. Branch Strategy
**Choice**: Created `feature/sales` branch instead of committing directly to `develop`
**Rationale**: Proper Git Flow — develop is for integration, not direct feature development

---

## Testing Capabilities

**Strict TDD Mode**: enabled ✅
**Detected**: 2026-05-07

### Test Runner
- Command: `./mvnw test` (unit) or `./mvnw verify` (integration)
- Framework: JUnit 5 (via spring-boot-starter-test)

### Test Layers
| Layer | Available | Tool |
|-------|-----------|------|
| Unit | ✅ | JUnit 5 + Mockito |
| Integration | ✅ | Spring Boot Test + H2 |
| E2E | ⚠️ | Testcontainers (CI only) |
| Coverage | ✅ | JaCoCo (70% minimum) |

---

## Sales Module Design (Complete)

### Technical Approach
Implement sales module following EXACTLY the pattern of previous modules. The key technical component is the **FIFO logic** in `SaleService`, using the EXISTING method `ProductionBatchRepository.findByProductIdAndCurrentStockGreaterThanOrderByProductionDateAsc()` to get batches ordered by date ascending (oldest first).

### Architecture Decisions

#### 1. Reuse Existing FIFO Method
**Choice**: Reuse existing repository method instead of creating new query
**Rationale**: Already tested, follows DRY principle, less code = fewer bugs

#### 2. Complete Module Structure
**Choice**: Create full structure: `domain/`, `repository/`, `service/`, `controller/`, `dto/`, `mapper/`
**Rationale**: Consistency with existing modules, clear separation of concerns, enables complete testing

#### 3. Security - ADMIN Only for MVP
**Choice**: Restrict `POST /api/v1/sales` to role **ADMIN** using `@PreAuthorize("hasRole('ADMIN')")`
**Rationale**: Follows pattern of other modules, sales affect inventory (sensitive operation)

### Data Flow

```
Client → SaleController → SaleService → ProductionBatchRepository
                                        ↓
                              ProductionBatch(es) with stock > 0 ordered by date ASC
                                        ↓
                              Iterate batches subtracting `currentStock`
                                        ↓
                              Create Sale + SaleItem(s) in @Transactional
                                        ↓
                              Return SaleResponse 201 Created
```

### Open Questions (for future development)
- [ ] Should sales include user information? (Not in MVP, future: add `user_id` to sales table)
- [ ] How to handle retries on `OptimisticLockException`? (MVP: return 409, future: auto-retry)
- [ ] Should system notify low stock after sale? (Future: Notifications module)

---

## User Preferences & Constraints

1. **Inline step-by-step** for educational purposes — user wants to see the full process, not just results
2. **Spanish comments** in all code for learning purposes
3. **Feature branches** required (feature/sales) instead of direct develop commits
4. **Save decisions to Engram** before continuing work
5. **Commit per phase** — atomic commits, not mega-commits
6. **Learning focus** — user wants to understand software engineering best practices WHILE advancing the project

---

## Deployment Configuration

- **Platform**: Railway.app (Docker-based)
- **Database**: MySQL 8.0 (managed by Railway)
- **Docker**: Multi-stage build (Maven + Eclipse Temurin 21)
- **Healthcheck**: `/` endpoint (Spring Boot default)
- **Environment Variables**:
  - `SPRING_PROFILES_ACTIVE=prod`
  - `SPRING_DATASOURCE_URL` (auto-injected by Railway)
  - `SPRING_DATASOURCE_USERNAME` (auto-injected)
  - `SPRING_DATASOURCE_PASSWORD` (auto-injected)
  - `JWT_SECRET` (custom variable)

---

## Project Conventions

### Code Style
- Java Records for DTOs (immutable)
- Constructor injection (no @Autowired)
- Jakarta Validation on request DTOs
- `@Transactional` on service methods that modify data
- `@PreAuthorize` for role-based access control
- OpenAPI annotations on controllers

### Git Conventions
- Conventional commits: `feat:`, `fix:`, `docs:`, `chore:`, `refactor:`
- Branch naming: `type/description` (e.g., `feature/sales`, `fix/auth-bug`)
- Atomic commits per phase/feature
- PR to develop branch, not main

### Testing Conventions
- Strict TDD: Write tests BEFORE code
- Unit tests: Mock dependencies, test business logic
- Integration tests: Use H2, TestRestTemplate, JWT tokens
- Coverage: 70% minimum (JaCoCo)

---

## Files Changed by Sales Module (for reference)

### New Files Created:
```
src/main/java/.../sales/domain/Sale.java
src/main/java/.../sales/domain/SaleItem.java
src/main/java/.../sales/dto/SaleRequest.java
src/main/java/.../sales/dto/SaleResponse.java
src/main/java/.../sales/dto/SaleItemResponse.java
src/main/java/.../sales/mapper/SaleMapper.java
src/main/java/.../sales/service/SaleService.java
src/main/java/.../sales/controller/SaleController.java
src/main/java/.../sales/repository/SaleRepository.java
src/main/java/.../sales/repository/SaleItemRepository.java
src/test/java/.../sales/service/SaleServiceTest.java
src/test/java/.../sales/mapper/SaleMapperTest.java
src/test/java/.../sales/controller/SaleControllerIT.java
```

### Modified Files:
```
src/main/java/.../security/config/SecurityConfig.java
src/main/java/.../common/handler/GlobalExceptionHandler.java
pom.xml (added JaCoCo plugin)
Dockerfile (renamed from Dockerfile.yml)
docker-compose.yml (added persistent volume)
```

---

## Current State (as of 2026-05-08)

✅ Sales Module: COMPLETE (6 phases, all tests passing)
✅ SDD Cycle: COMPLETE (explore → propose → spec → design → tasks → apply → verify → archive)
✅ Docker: Configured with persistent volume
✅ Railway: Configured with railway.json
✅ Documentation: API_REFERENCE.md, BACKEND_CONTEXT.md, STUDY_GUIDE.md, PROJECT_HISTORY.md
✅ Coverage: JaCoCo configured and passing (70% minimum)
✅ Git: Pushed to feature/sales branch
🔲 PR: To be created manually by user
🔲 Frontend: Flutter project to be created
