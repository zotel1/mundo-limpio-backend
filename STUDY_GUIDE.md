# Guía de Estudio - Mundo Limpio Backend

> **Propósito**: Archivo vivo con todos los conceptos técnicos sugeridos durante el desarrollo del proyecto.  
> **Actualizado**: 2026-05-08 (Sales Module completo - Phase 6)  
> **Mantenido por**: Gentle AI (big-pickle) + zotel1

---

## Índice

1. [Metodologías y Procesos](#1-metodologías-y-procesos)
2. [Arquitectura de Software](#2-arquitectura-de-software)
3. [Patrones de Diseño](#3-patrones-de-diseño)
4. [Java y Spring Boot](#4-java-y-spring-boot)
5. [Base de Datos](#5-base-de-datos)
6. [Testing](#6-testing)
7. [Seguridad](#7-seguridad)
8. [Herramientas y Configuración](#8-herramientas-y-configuración)
9. [CI/CD y DevOps](#9-cicd-y-devops)
10. [Específicos del Proyecto](#10-específicos-del-proyecto)
11. [IA y Agentes](#11-ia-y-agentes)

---

## 1. Metodologías y Procesos

### 1.1 Spec-Driven Development (SDD)
Metodología de desarrollo que separa el diseño de la implementación. El flujo es: Explore → Propose → Spec → Design → Tasks → Apply → Verify → Archive. Garantiza que se piense antes de codificar.

### 1.2 Conventional Commits
Estándar para mensajes de git: `feat:`, `fix:`, `chore:`, `docs:`, `style:`, `refactor:`, `perf:`, `test:`, `build:`, `ci:`, `revert:`. Formato: `type(scope): description`.

### 1.3 Given/When/Then (BDD)
Formato para especificaciones de comportamiento:
- **Given**: Estado inicial
- **When**: Acción que dispara el comportamiento
- **Then**: Resultado esperado

### 1.4 RFC 2119 Keywords
Palabras clave para especificaciones técnicas:
- **MUST** / **REQUIRED**: Obligatorio absoluto
- **SHALL**: Obligatorio (sin excepciones)
- **SHOULD** / **RECOMMENDED**: Se recomienda fuertemente pero hay excepciones válidas
- **MAY** / **OPTIONAL**: Opcional

### 1.5 Strict TDD (Test-Driven Development)
Modalidad donde los tests se escriben ANTES que el código. Ciclo: Red (test falla) → Green (código mínimo para pasar) → Refactor.

### 1.6 Exploring Requirements
Proceso de investigación de necesidades antes de codificar. Incluye: entrevistas, análisis de dominio, revisión de código existente.

### 1.7 Domain-Driven Analysis
Análisis centrado en el dominio del negocio. Identificar entidades, value objects, agregados, repositorios, servicios del dominio.

### 1.8 Risk Assessment
Evaluación de riesgos técnicos: complejidad, acoplamiento, concurrencia, integridad de datos, seguridad.

### 1.9 Proposal Writing
Cómo escribir propuestas técnicas: Intent, Scope (In/Out), Approach, Risks, Rollback Plan, Success Criteria.

### 1.10 Capabilities Section (SDD)
Sección clave en propuestas SDD. Define: New Capabilities (se vuelven specs) y Modified Capabilities (se vuelven delta specs).

### 1.11 Rollback Plan
Plan de contingencia: cómo revertir un cambio si algo sale mal. Ej: `git revert`, `DROP TABLE`, `systemctl restart`.

### 1.12 Success Criteria
Criterios medibles de éxito: checklist de cómo saber que el cambio funcionó.

### 1.13 MVP (Minimum Viable Product)
Versión mínima de una feature para validar hipótesis de negocio. En este proyecto: Sales Module con solo POST y validaciones básicas.

### 1.14 Scope Definition
Definición clara de qué está en alcance (In Scope) y qué está fuera (Out of Scope). Evita el scope creep.

---

## 2. Arquitectura de Software

### 2.1 Layered Architecture (Arquitectura por Capas)
Organización en capas: Controller → Service → Repository → Domain. Cada capa tiene una responsabilidad única.

### 2.2 Entry Points
Puntos de entrada a un sistema: controladores REST, handlers de mensajes, listeners de eventos. Dónde empezar a investigar.

### 2.3 SOLID Principles
Principios de diseño de objetos:
- **S**: Single Responsibility
- **O**: Open/Closed
- **L**: Liskov Substitution
- **I**: Interface Segregation
- **D**: Dependency Inversion

---

## 3. Patrones de Diseño

### 3.1 DTO Pattern (Data Transfer Object)
Objetos para transferir datos entre capas. En este proyecto: `ProductRequest`, `ProductResponse`. Separan la API de la entidad JPA.

### 3.2 Repository Pattern
Abstracción del acceso a datos. Spring Data JPA provee: `ProductRepository extends JpaRepository<Product, Long>`.

### 3.3 Factory Method (implícito en Spring)
`@Bean` en configuración crea objetos. Spring maneja el ciclo de vida.

### 3.4 Singleton (implícito en Spring)
Todos los `@Service`, `@Controller`, `@Repository` son singletons por defecto en Spring.

### 3.5 Strategy Pattern
Familia de algoritmos intercambiables. Ej: `JwtAuthenticationFilter` como estrategia de autenticación.

### 3.6 Observer Pattern
Notificaciones de un evento a múltiples suscriptores. Para alertas de stock bajo.

### 3.7 Builder Pattern
Construcción paso a paso de objetos complejos. Lombok `@Builder`.

### 3.8 Saga Pattern
Gestión de transacciones distribuidas. Para ventas que involucran múltiples servicios.

### 3.9 Facade Pattern
Interfaz simplificada para un subsistema complejo.

---

## 4. Java y Spring Boot

### 4.1 Spring Boot Test
Anotaciones: `@SpringBootTest`, `@WebMvcTest`, `@DataJpaTest`, `@RestClientTest`.

### 4.2 TestRestTemplate
Cliente HTTP para tests de integración de APIs REST.

### 4.3 Mockito
Biblioteca para mocks: `@Mock`, `@InjectMocks`, `when().thenReturn()`, `verify()`.

### 4.4 AssertJ
Assertions fluidas: `assertThat(result).isNotNull().isEqualTo(expected)`.

### 4.5 H2 Database
Base de datos in-memory para tests. Configurada en `application-test.yml`.

### 4.6 JUnit 5
Framework de testing: `@Test`, `@BeforeEach`, `@AfterEach`, `@DisplayName`, `@Nested`.

### 4.7 Mockito Annotations
`@ExtendWith(MockitoExtension.class)`, `@Mock`, `@InjectMocks`.

### 4.8 Spring Boot Test Slices
Tests focalizados: `@WebMvcTest` (solo web), `@DataJpaTest` (solo JPA).

### 4.9 Lombok
Reducción de boilerplate: `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Slf4j`.

### 4.10 @ManyToOne
Relación JPA: muchos registros hijos apuntan a un padre. Ej: `ProductionBatch` tiene `Product`.

### 4.11 FetchType.LAZY vs EAGER
- **LAZY**: Carga perezosa (solo cuando se accede al atributo)
- **EAGER**: Carga inmediata (siempre con la entidad padre)

### 4.12 @Version (Optimistic Locking)
Control de concurrencia: evita condiciones de carrera. Si hay conflicto → `OptimisticLockException`.

### 4.13 BigDecimal
Clase para números decimales precisos. USA ESTO para dinero, NO uses `double` (problemas de precisión).

### 4.14 @Transactional
Gestión de transacciones ACID. Si algo falla, hace rollback automático.

### 4.15 @OneToMany y @ManyToOne
Relaciones JPA bidireccionales. Ej: `Sale` (uno) → `SaleItem` (muchos).

### 4.16 Cascade Types
Qué pasa cuando borrás una entidad padre:
- **PERSIST**: Guarda hijos al guardar padre
- **MERGE**: Actualiza hijos al actualizar padre
- **REMOVE**: Borra hijos al borrar padre (¡cuidado!)
- **ALL**: Todos los anteriores
- **RESTRICT**: No permite borrar si hay hijos (usado en este proyecto)

### 4.17 Java Records
DTOs inmutables (Java 14+): `record ProductRequest(String sku, String name) {}`.

---

## 5. Base de Datos

### 5.1 Flyway Migrations
Control de versiones de base de datos. Archivos `V1__Initial_Schema.sql`, `V2__Add_Users.sql`, etc.

### 5.2 Flyway Migrations Order
Orden de ejecución: V1 → V2 → V3. Flyway lleva registro en tabla `flyway_schema_history`.

### 5.3 JPA Entity Mapping
Mapeo de tablas a clases Java: `@Entity`, `@Table`, `@Column`, `@Id`, `@GeneratedValue`.

### 5.4 Foreign Keys RESTRICT vs CASCADE
- **RESTRICT**: No permite borrar si hay referencias (integridad referencial)
- **CASCADE**: Borra en cascada (peligroso)

### 5.5 FIFO (First In, First Out)
Algoritmo de inventario: "el primero que entra es el primero que sale". Descontar del lote más antiguo.

### 5.6 FIFO Algorithm
Implementación: ordenar lotes por `productionDate` ascendente, iterar hasta cubrir la cantidad vendida.

### 5.7 FIFO Race Conditions
Condiciones de carrera en FIFO: múltiples ventas simultáneas. Mitigación: `@Version` (Optimistic Locking).

### 5.8 Snapshot Pattern
Guardar valores en el momento exacto de la transacción. Ej: `unitCostAtSale` en `SaleItem` guarda el costo del lote al momento de la venta. Si el costo del lote cambia después, el registro histórico no se altera. Esencial para contabilidad y auditoría.

### 5.9 Atomicity (@Transactional)
`@Transactional` garantiza que todas las operaciones dentro del método se ejecutan como una unidad atómica: o todas se commitean o ninguna. Si una excepción ocurre, se hace rollback automático. Sin esto, una venta fallida podría dejar stock inconsistente.

---

## 6. Testing

### 6.1 Integration vs Unit Tests
- **Unit**: Prueba una unidad aislada (un método, una clase)
- **Integration**: Prueba la integración entre capas (Controller + Service + Repository)

### 6.2 JaCoCo
Herramienta de cobertura de código. Genera reportes: `./mvnw test jacoco:report`.

### 6.3 JaCoCo Maven Plugin
Configuración en `pom.xml`:
```xml
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <version>0.8.12</version>
</plugin>
```

### 6.4 GitHub Actions artifacts
Guardar archivos generados en CI: `actions/upload-artifact@v4`.

### 6.5 Testcontainers
Contenedores Docker para tests de integración reales. Soporta MySQL, PostgreSQL, Redis, etc.

### 6.6 Testcontainers with MySQL
```java
@TestConfiguration
public class TestConfig {
    @Bean
    public MySQLContainer<?> mysqlContainer() {
        return new MySQLContainer<>("mysql:8")
            .withDatabaseName("test");
    }
}
```

### 6.7 Table-Driven Tests (Go)
Patrón en Go para múltiples casos:
```go
tests := []struct {
    name     string
    input    string
    expected string
}{...}
```

### 6.8 E2E Tests (End-to-End)
Pruebas de toda la aplicación: desde el frontend hasta la base de datos.

### 6.9 SaleService Logic
Lógica de negocio para ventas:
1. Validar stock disponible
2. Obtener lotes FIFO (`findByProductIdAndCurrentStockGreaterThanOrderByProductionDateAsc`)
3. Iterar lotes restando `currentStock`
4. Crear `Sale` y `SaleItem`
5. Guardar todo en `@Transactional`

### 6.10 Integration Tests con JWT
Patrón para tests de integración con autenticación:
```java
@BeforeEach
void setUp() {
    User admin = new User("admin_test", "password", Role.ADMIN);
    userRepository.save(admin);
    String token = jwtService.generateToken(admin);
    adminHeaders = new HttpHeaders();
    adminHeaders.setBearerAuth(token);
}
```
**Importante**: Crear usuario con password en texto plano funciona en tests porque Spring no valida la password en JWT (solo el username). En producción, la password DEBE estar hasheada con BCrypt.

### 6.11 Security Integration Test Matrix
Tests esenciales para cualquier endpoint protegido:
| Test | Qué verifica | Status HTTP |
|------|--------------|-------------|
| Sin token | Spring Security bloquea | 401 |
| Token con rol incorrecto | @PreAuthorize bloquea | 403 |
| Token correcto + datos válidos | Endpoint funciona | 201/200 |
| Token correcto + datos inválidos | Validación de negocio | 400 |

### 6.12 Atomicity Testing
Verificar que operaciones fallidas NO modifican el estado:
1. Crear dato con valor conocido (stock = 5)
2. Intentar operación inválida (vender 100)
3. Verificar que el dato NO cambió (stock sigue = 5)

---

## 7. Seguridad

### 7.1 JWT Authentication (JSON Web Tokens)
Tokens firmados para autenticación stateless. Estructura: Header.Payload.Signature.

### 7.2 RBAC (Role-Based Access Control)
Control de acceso basado en roles: `@PreAuthorize("hasRole('ADMIN')")`.

### 7.3 Refresh Tokens
Tokens de larga duración para obtener nuevos access tokens sin re-login.

### 7.4 Rate Limiting
Limitar cantidad de requests por IP/usuario en un tiempo determinado.

### 7.5 BCryptPasswordEncoder
Algoritmo de hashing para passwords: `new BCryptPasswordEncoder()`.

### 7.6 HttpStatusEntryPoint
Spring Security retorna 403 por defecto para requests no autenticados. Para APIs REST, necesitás 401. Solución:
```java
.exceptionHandling(ex -> ex
    .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
)
```
**Bug crítico descubierto**: `JwtAuthenticationFilter` tenía `if (username == null && ...)` en vez de `if (username != null && ...)`. La autenticación JWT NO funcionaba desde el inicio.

### 7.7 GlobalExceptionHandler para Seguridad
Excepciones de seguridad que deben tener handlers:
- `AccessDeniedException` → 403 Forbidden (usuario autenticado pero sin permiso)
- `IllegalArgumentException` → 400 Bad Request (validación de negocio, ej: stock insuficiente)
- `OptimisticLockingFailureException` → 409 Conflict (concurrencia detectada por @Version)

Sin estos handlers, las excepciones caen como 500 Internal Server Error — incorrecto para REST.

---

## 8. Herramientas y Configuración

### 8.1 Maven Wrapper (mvnw)
Maven empaquetado en el proyecto. Garantiza misma versión para todos: `./mvnw clean install`.

### 8.2 YAML Syntax
Sintaxis de YAML: indentación con espacios (NO tabs), `key: value`, listas con `-`.

### 8.3 YAML Frontmatter
Metadatos en archivos Markdown (usado en Skills):
```markdown
---
name: skill-name
description: >
  Description with trigger.
---
```

### 8.4 gitignore
Archivos a ignorar en git: `.atl/`, `target/`, `.idea/`, `*.log`.

### 8.5 Markdown Tables
Formato para tablas en Markdown:
```markdown
| Columna 1 | Columna 2 |
|------------|-----------|
| Dato 1     | Dato 2    |
```

---

## 9. CI/CD y DevOps

### 9.1 GitHub Actions CI
Pipelines de integración continua: `.github/workflows/ci.yml`.

### 9.2 GitHub Actions CI Configuration
```yaml
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
```

### 9.3 SonarCloud
Análisis de calidad de código: bugs, vulnerabilidades, code smells, coverage.

### 9.4 Spring Boot Actuator
Endpoints de monitoreo: `/actuator/health`, `/actuator/metrics`.

### 9.5 Prometheus + Grafana
Monitoreo y dashboards. Prometheus recolecta métricas, Grafana las visualiza.

---

## 10. Específicos del Proyecto

### 10.1 SOFT DELETE
No borrar físicamente: usar campo `active = false`. Mantiene integridad referencial.

### 10.2 Sales Module Domain
Entidades: `Sale` (id, totalAmount, createdAt, @Version) y `SaleItem` (id, sale, productionBatchId, quantity, unitPriceAtSale, unitCostAtSale). Relación bidireccional con `cascade = ALL, orphanRemoval = true`.

### 10.3 Sales Module Endpoints (implementados)
- POST `/api/v1/sales` → Registrar venta (FIFO) ✅ IMPLEMENTADO - ADMIN only
- GET `/api/v1/sales` → Listar ventas ❌ Pendiente
- GET `/api/v1/sales/{id}` → Obtener por ID ❌ Pendiente

### 10.5 Sales Module - Critical Bugs Found
1. **JWT Filter Bug**: `username == null` en vez de `!= null` — la autenticación no funcionaba
2. **401 vs 403**: Spring retorna 403 por defecto — necesitaba HttpStatusEntryPoint
3. **Missing Exception Handlers**: IllegalArgumentException, AccessDeniedException, OptimisticLockingFailureException caían como 500

### 10.6 Sales Module - Integration Tests
8 tests implementados en `SaleControllerIT`:
- 401 No Token, 403 As Operator, 201 Success, 400 Insufficient Stock
- FIFO Multiple Batches, Partial Deduction, Failed Sale Stock Not Modified, Optimistic Locking

### 10.4 Inventory Module (futuro)
Gestión de stock de productos envasados. Diferencia: `production_batches` (lotes) vs `inventory` (stock final).

---

## 11. IA y Agentes

### 11.1 Skill Resolver Protocol
Protocolo para resolver qué skills cargar según el contexto. Busca en registry, matchea triggers, inyecta compact rules.

### 11.2 Sub-agents
Agentes especializados que ejecutan tareas específicas. Lanzados via `delegate` o `task`.

### 11.3 Compact Rules
Reglas concisas (5-15 líneas) que reciben los sub-agents. Ej: "Never use double for money → use BigDecimal".

### 11.4 .atl/ directory
Carpeta de infraestructura del proyecto. Contiene `skill-registry.md`.

### 11.5 Agent Skills Spec
Formato para skills de IA: `SKILL.md` con YAML frontmatter (name, description con Trigger, license).

### 11.6 Blind Review
Revisión ciega: dos revisores independientes sin saber uno del otro. Evita sesgos.

### 11.7 SDD Persistence Modes
- **engram**: Base de datos local (solo, no compartible)
- **openspec**: Archivos en `openspec/` (compartible, git-friendly)
- **hybrid**: Archivos + engram (recuperación ante fallos)
- **none**: Solo en memoria (se pierde al terminar)

### 11.8 openspec/ directory
Estructura: `openspec/config.yaml`, `openspec/specs/`, `openspec/changes/`, `openspec/changes/archive/`.

### 11.9 Engram Persistence
Almacenamiento clave-valor persistente. `mem_save`, `mem_search`, `mem_get_observation`.

### 11.10 Topic Keys
Claves para upserts en Engram. Ej: `sdd-init/mundo-limpio-backend`. Evita duplicados.

### 11.11 Project Context
Información esencial del stack detectado. Guardado en Engram con `type: "architecture"`.

### 11.12 sdd-phase-common.md
Protocolo común para fases SDD. Carga de skills, recuperación de artifactos, persistencia, return envelope.

### 11.13 Artifact Persistence
Guardar artifactos SDD en Engram o archivos. MANDATORIO para que las fases siguientes encuentren el trabajo.

### 11.14 Return Envelope
Formato estructurado que devuelve cada fase SDD:
```markdown
**Status**: success | partial | blocked
**Summary**: 1-3 oraciones
**Artifacts**: Engram keys o paths
**Next**: sdd-propose | sdd-spec | none
**Risks**: Riesgos descubiertos
**Skill Resolution**: injected | fallback-registry | fallback-path | none
```

### 11.15 sdd-explore phase
Fase de investigación. NO modifica código. Lee el codebase, analiza opciones, persiste `exploration.md`.

### 11.16 sdd-propose phase
Fase de propuesta. Define intención, alcance (In/Out Scope), enfoque, riesgos, plan de rollback, success criteria.

### 11.17 sdd-spec phase

### 11.17 sdd-spec phase
Fase de especificaciones. Given/When/Then, RFC 2119 keywords, escenarios felices y error.

### 11.18 RFC 2119 Keywords Quick Reference
| Keyword | Meaning |
|---------|---------|
| **MUST / SHALL** | Absolute requirement |
| **MUST NOT / SHALL NOT** | Absolute prohibition |
| **SHOULD** | Recommended, but exceptions may exist with justification |
| **SHOULD NOT** | Not recommended, but may be acceptable with justification |
| **MAY** | Optional |

### 11.19 Delta Spec Format
Formato para specs que modifican comportamiento existente:
```markdown
## ADDED Requirements
### Requirement: {Name}
(The system MUST...)

#### Scenario: {Happy path}
- GIVEN {precondition}
- WHEN {action}
- THEN {outcome}

## MODIFIED Requirements
### Requirement: {Existing Name}
(Full updated requirement text - replaces existing entirely)
(Previously: {what it was before})

## REMOVED Requirements
### Requirement: {Being Removed}
(Reason: {why})
```

### 11.20 New Spec Format
Para dominios completamente nuevos (sin spec previa):
```markdown
# {Domain} Specification

## Purpose
{High-level description}

## Requirements
### Requirement: {Name}
The system MUST {behavior}.

#### Scenario: {Name}
- GIVEN {precondition}
- WHEN {action}
- THEN {outcome}
```

### 11.18 sdd-design phase
Fase de diseño técnico. Diagramas de secuencia, decisiones de arquitectura con rationale.

### 11.19 sdd-tasks phase
Fase de descomposición. Tareas jerárquicas (1.1, 1.2), agrupadas por fases.

### 11.20 sdd-apply phase
Fase de implementación. Codear siguiendo specs, diseño y tareas.

### 11.21 sdd-design phase
Fase de diseño técnico. Diagramas de secuencia, decisiones de arquitectura con rationale.

### 11.22 Sequence Diagrams (Mermaid)
Cómo dibujar flujo de llamadas: `participant A`, `A->>B: Request`, `B-->>A: Response`. Usado en sdd-design.

### 11.23 Architecture Decision Record (ADR)
Documentar decisiones de arquitectura: Context, Decision, Consequences. Formato: `## Context`, `## Decision`, `## Consequences`.

### 11.24 Java Method Signatures
`public ReturnType methodName(ParamType param)` - diseño de firmas para sdd-design.

### 11.25 FIFO Technical Implementation
Iteración sobre colecciones ordenadas: `for (Batch b : batches)`, uso de `BigDecimal.subtract()` para restas precisas.

### 11.26 sdd-design phase
Fase de diseño técnico. Diagramas de secuencia, decisiones de arquitectura con rationale.

### 11.27 Architecture Decision Record (ADR)
Documentar decisiones de arquitectura: Context, Decision, Consequences. Formato: `## Context`, `## Decision`, `## Consequences`.

### 11.28 Sequence Diagrams (Mermaid)
Cómo dibujar flujo de llamadas: `participant A`, `A->>B: Request`, `B-->>A: Response`. Usado en sdd-design.

### 11.29 Java Method Signatures
`public ReturnType methodName(ParamType param)` - diseño de firmas para sdd-design.

### 11.30 DRY Principle
Don't Repeat Yourself. Reutilizar métodos existentes (ej: `findAllWithStockForFifo()`) en lugar de reinventar.

### 11.31 sdd-tasks phase
Fase de descomposición. Tareas jerárquicas (1.1, 1.2), agrupadas por fases (Foundation, Core Implementation, Testing).

### 11.32 Task Writing Rules
Cada tarea DEBE ser:
- **Specific**: "Create `Sale.java` with JPA annotations" ✅ vs "Add sale" ❌
- **Actionable**: "Add `validateStock()` method" ✅ vs "Handle stocks" ❌
- **Verifiable**: "Test: `POST /sales` returns 401 without token" ✅ vs "Make sure it works" ❌
- **Small**: One file or one logical unit of work ✅ vs "Implement the feature" ❌

### 11.33 Phase Organization Guidelines
| Phase | Focus |
|--------|-------|
| Phase 1: Foundation | Types, interfaces, DB changes, config - things others depend on |
| Phase 2: Core Implementation | Main logic, business rules, core behavior |
| Phase 3: Integration/Wiring | Connect components, routes, UI wiring |
| Phase 4: Testing | Unit tests, integration tests, e2e tests |
| Phase 5: Cleanup | Documentation, remove dead code, polish |

### 11.34 Strict TDD in Tasks
For TDD projects, each feature task becomes 3 sub-tasks:
1. **RED**: Write failing test (task 1.1)
2. **GREEN**: Minimal code to pass (task 1.2)
3. **REFACTOR**: Clean up (task 1.3)

### 11.35 sdd-apply phase
Fase de implementación: codear siguiendo specs, diseño y tareas con Strict TDD.

### 11.36 RED-GREEN-REFACTOR cycle
Ciclo TDD: escribir test que falla (RED), código mínimo para pasar (GREEN), limpiar (REFACTOR).

### 11.37 JPA Entity Annotations
`@Entity`, `@Table`, `@Id`, `@GeneratedValue`, `@ManyToOne`, `@OneToMany`, `@Version` - anotaciones para mapeo JPA.

### 11.38 Java Records for DTOs
`record Name(Type field) {}` - inmutables, sin boilerplate, ideales para DTOs. 

### 11.39 Jakarta Validation Constraints
`@NotNull`, `@NotBlank`, `@Positive`, `@Pattern(regexp = "...")`, `@Valid` - validación de entrada. 

### 11.40 strict-tdd.md module
Módulo que se carga cuando Strict TDD Mode está activo. OVERRIDE del paso normal de implementación. Requiere tabla TDD Cycle Evidence. 

### 11.41 TDD Cycle Evidence
Tabla obligatoria cuando Strict TDD está activo: 
```
| Task | RED (test written) | GREEN (impl passes) | REFACTOR (clean up) |
|------|-------------------|----------------------|-------------------|
| 1.1   | ✅                 | ✅                    | ✅                 |
```

### 11.42 sdd-verify phase
Fase de verificación. Correr tests, comparar implementación contra specs.

### 11.22 sdd-archive phase
Fase de archivo. Sync delta specs a main specs, limpiar, guardar resumen.

### 11.23 Branch-PR Workflow
Flujo con GitHub: feature branch → PR a develop → PR develop a main. Conventional Commits en commits.

### 11.24 Issue Creation
Crear issues con templates (bug_report, feature_request). Cada issue necesita `status:approved` antes de abrir PR.

### 11.25 Judgment Day
Protocolo de revisión adversarial doble ciego. Dos jueces independientes → síntesis → fix → re-juzgar (máx 2 iteraciones).

### 11.26 Go Testing Patterns
Table-driven tests, golden file testing, `teatest` para Bubbletea TUI.

---

## 12. Conceptos Futuros (pendientes de agregar)

> **Nota**: Esta sección se completará automáticamente cuando sugiera nuevos conceptos en futuras sesiones.

### ✅ Completados en esta sesión:
- [x] Integration Tests con JWT y Spring Security
- [x] Snapshot Pattern (costo al momento de venta)
- [x] Atomicity testing (@Transactional rollback)
- [x] HttpStatusEntryPoint para 401 responses
- [x] GlobalExceptionHandler para seguridad (400, 403, 409)
- [x] FIFO con múltiples lotes (complejo)
- [x] Optimistic Locking verification
- [x] Bidirectional JPA relationships con helper methods

### Pendientes:
- [ ] Pagination (paginación en endpoints GET)
- [ ] Caching with Redis
- [ ] Aspect-Oriented Programming (@Aspect)
- [ ] Caching with Redis
- [ ] Aspect-Oriented Programming (@Aspect)
- [ ] Saga Pattern (para transacciones distribuidas) - YA EXPLICADO EN 3.8
- [ ] Observer Pattern (para notificaciones) - YA EXPLICADO EN 3.6
- [ ] Builder Pattern (para objetos complejos) - YA EXPLICADO EN 3.7
- [ ] Factory Pattern (creación de objetos) - YA EXPLICADO EN 3.3
- [ ] Facade Pattern (interfaz simplificada) - YA EXPLICADO EN 3.9
- [ ] DTO vs DAO vs VO (diferencias)
- [x] Jakarta Validation (@NotBlank, @NotNull, @Positive) - EXPLICADO EN 11.39
- [ ] OpenAPI/Swagger annotations (@Operation, @ApiResponse)
- [ ] Spring Security Filter Chain
- [ ] JWT Structure (Header, Payload, Signature)
- [ ] Refresh Token Rotation - YA EXPLICADO EN 7.3
- [ ] RBAC vs ABAC (Attribute-Based Access Control)
- [ ] Docker Compose networking
- [ ] MySQL vs PostgreSQL (diferencias)
- [ ] H2 vs HSQLDB vs Derby (bases in-memory)
- [ ] Maven Profiles (test, dev, prod)
- [ ] Spring Profiles (@Profile)
- [ ] Liquibase vs Flyway (alternativas)
- [ ] Test Doubles (Mock, Stub, Fake, Spy)
- [ ] Consumer-Driven Contracts (CDC)
- [ ] Contract Testing (Pact)
- [ ] Mutation Testing (PIT)
- [ ] Property-Based Testing (junit-quickcheck)
- [ ] Chaos Engineering (resiliencia)
- [ ] Circuit Breaker Pattern
- [ ] Bulkhead Pattern
- [ ] Retry Pattern
- [ ] Timeout Pattern
- [x] Bidirectional JPA relationships - EXPLICADO EN 4.15
- [x] Cascade Types (ALL, orphanRemoval) - EXPLICADO EN 4.16
- [x] Snapshot Pattern for financial records - NUEVO EN 5.8
- [x] Atomicity testing with @Transactional - NUEVO EN 5.9

---

## Cómo usar esta guía

1. **Durante el desarrollo**: Cuando aparezca un concepto nuevo, leé su definición aquí
2. **Para estudio profundo**: Buscar recursos externos (documentación oficial, tutoriales) basados en estas definiciones
3. **Para revisión**: Antes de una sesión nueva, repasá los conceptos de la sección que vamos a tocar

---

**¡Ponete las pilas y estudiá estos conceptos, hermano! 💪**
