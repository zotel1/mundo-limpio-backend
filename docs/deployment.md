> **⚠️ DEPLOYMENT MIGRATED**: This project now deploys to **Render** instead of Google Cloud Run.
> The old Cloud Run deployment guide has been removed. For the current pipeline, see
> `render.yaml` and `.github/workflows/render-deploy.yml`.

## 🚀 Render Deployment Guide

### Arquitectura

El deploy de MundoLimpio en Render sigue un flujo simple: GitHub Action → Deploy Hook → Render build → Flyway migrations → app live.

```
┌──────────────┐   push a main    ┌──────────────────┐   POST hook    ┌────────────────┐
│   Developer   │ ────────────────> │  GitHub Action    │ ────────────> │     Render      │
│  git push     │                  │ render-deploy.yml │              │  Deploy Hook    │
└──────────────┘                  └──────────────────┘              └───────┬────────┘
                                                                           │
                                                                    ┌──────▼────────┐
                                                                    │  Render Build  │
                                                                    │ Dockerfile     │
                                                                    │ multi-stage    │
                                                                    └──────┬────────┘
                                                                           │
                                                                    ┌──────▼────────┐
                                                                    │  Spring Boot   │
                                                                    │ Flyway V5→V7   │
                                                                    │ /actuator/...  │
                                                                    └──────┬────────┘
                                                                           │ SSL
                                                                    ┌──────▼────────┐
                                                                    │   Supabase     │
                                                                    │ PostgreSQL 16  │
                                                                    └───────────────┘
```

**Componentes**:

- **WHAT: Render**: Plataforma cloud que construye la imagen Docker desde el `Dockerfile` multi-stage (Maven + JRE),
  ejecuta el contenedor y maneja TLS, health checks y scaling automáticamente.
  Región: Ohio (us-east-2). Plan: Starter.
  **DIFFERENCES con GCP**: No necesita Artifact Registry — Render construye la imagen directamente.

- **WHAT: GitHub Action** (`.github/workflows/render-deploy.yml`): Se dispara en cada push a
  `main`. Hace un POST al Deploy Hook de Render para iniciar el deploy.
  El hook URL se almacena como secreto de GitHub (`RENDER_DEPLOY_HOOK`), nunca en el código.
  **WHY**: `autoDeploy: false` en `render.yaml` — el action es el ÚNICO trigger de deploy,
  evitando doble-deploy si Render también tiene GitHub integration.

- **WHAT: Supabase PostgreSQL 16**: Base de datos gestionada. Conexión via SSL usando
  el pooler de PgBouncer (port 6543). Las migraciones Flyway se ejecutan automáticamente
  al iniciar Spring Boot.

- **WHAT: Flyway**: Ejecuta migraciones pendientes al inicio de la app. Hace baseline en V5
  (las migraciones V1-V5 ya fueron aplicadas en Supabase). Solo aplica V6 y V7 en el
  primer deploy a Render. **WHY**: Idempotente — deploys subsiguientes no re-ejecutan migraciones.

### Prerrequisitos

- Cuenta de Render con el repositorio de GitHub conectado
- Base de datos PostgreSQL en Supabase (ya aprovisionada)
- Secrets de GitHub configurados: `RENDER_DEPLOY_HOOK`

No se necesita `gcloud` CLI, Artifact Registry, ni Cloud Build — Render reemplaza
todo el stack de GCP.

### Cómo Funciona

1. Developer pushea a `develop` → PR review → merge a `main`
2. GitHub Action (`render-deploy.yml`) se dispara automáticamente en el push a `main`
3. El action hace `curl -X POST` al Render Deploy Hook URL con timeout de 30s
4. Render hace pull del último commit de `main`, construye la imagen Docker con el `Dockerfile` multi-stage (Maven 3.9.9 → JRE 21 Alpine)
5. Spring Boot inicia con perfil `prod` → Flyway baselines en V5, ejecuta migraciones pendientes → health check responde 200
6. La app está viva en `https://mundolimpio-api.onrender.com`

### Render Blueprint (`render.yaml`)

```yaml
services:
  - type: web
    name: mundolimpio-api
    runtime: docker
    plan: starter
    region: ohio
    branch: main
    healthCheckPath: /actuator/health/liveness
    autoDeploy: false
    preDeployCommand: ""
    envVars:
      - key: SPRING_PROFILES_ACTIVE
        value: prod
      - key: PGHOST
        sync: false
      - key: PGPORT
        value: "6543"
      - key: PGDATABASE
        value: postgres
      - key: PGUSER
        sync: false
      - key: PGPASSWORD
        sync: false
      - key: JWT_SECRET_KEY
        sync: false
      - key: APPLICATION_CORS_ALLOWED_ORIGINS
        sync: false
```

- **autoDeploy: false** — Solo el GitHub Action dispara el deploy. Esto evita
  doble-deploy si Render también tiene GitHub integration configurada.
- **healthCheckPath: /actuator/health/liveness** — Render usa este endpoint para
  verificar que el deploy fue exitoso. Responde 200 cuando Spring Boot está listo.
- **sync: false** en env vars — Render no sobreescribe estos valores cuando se
  actualiza el blueprint. El usuario los configura UNA VEZ en el dashboard de Render
  y persisten entre deploys.
- **preDeployCommand: ""** — Flyway corre en el startup de la JVM
  (`spring.flyway.enabled: true`). No se necesita comando pre-deploy porque el
  Dockerfile ya incluye las migraciones en el classpath.

### Configuración del Deploy Hook

1. En Render Dashboard → proyecto **Settings** → **Deploy Hook** → **Create**
2. Copiar la URL completa (es un token opaco, no compartirla)
3. En GitHub repo → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**
   - Nombre: `RENDER_DEPLOY_HOOK`
   - Valor: la URL completa del hook
4. El GitHub Action usa `${{ secrets.RENDER_DEPLOY_HOOK }}` — GitHub redacta
   automáticamente los secrets de los logs, así que la URL nunca aparece expuesta.

### Variables de Entorno

| Variable | Dónde se configura | Propósito |
|----------|-------------------|-----------|
| `SPRING_PROFILES_ACTIVE` | render.yaml (fijo: `prod`) | Activa perfil de producción |
| `PGHOST` | Render Dashboard (sync: false) | Host del pooler de Supabase |
| `PGPORT` | render.yaml (fijo: `6543`) | Puerto PgBouncer de Supabase |
| `PGDATABASE` | render.yaml (fijo: `postgres`) | Nombre de la base de datos |
| `PGUSER` | Render Dashboard (sync: false) | Usuario de Supabase (`postgres.{ref}`) |
| `PGPASSWORD` | Render Dashboard (sync: false) | Password de Supabase |
| `JWT_SECRET_KEY` | Render Dashboard (sync: false) | Clave secreta JWT (mín 256 bits) |
| `APPLICATION_CORS_ALLOWED_ORIGINS` | Render Dashboard (sync: false) | Orígenes CORS permitidos (frontend URL) |
| `RENDER_DEPLOY_HOOK` | GitHub Secrets | URL del Deploy Hook de Render |

**Fixas**: `SPRING_PROFILES_ACTIVE`, `PGPORT`, `PGDATABASE` — definidas en `render.yaml`,
no necesitan configuración manual.

**Secretas**: `PGHOST`, `PGUSER`, `PGPASSWORD`, `JWT_SECRET_KEY`, `APPLICATION_CORS_ALLOWED_ORIGINS` —
se configuran en el dashboard de Render (sync: false) y NUNCA se commitean al repositorio.

### Migraciones Flyway

Flyway se ejecuta automáticamente al iniciar Spring Boot (`spring.flyway.enabled: true`
en `application-prod.yml`). No se necesita intervención manual.

```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    baseline-version: 5
```

- **WHAT: baseline-on-migrate: true** — Permite que Flyway no falle al encontrar una DB
  de Supabase que ya tiene migraciones aplicadas en `flyway_schema_history`.
  Hace baseline automáticamente y solo aplica las migraciones pendientes (V6+).
  **WHY**: Sin esto, el primer deploy a Render fallaría porque Flyway encuentra una DB
  no vacía sin poder rastrear su historial.

- **WHAT: baseline-version: 5** — Le dice a Flyway que todo hasta V5 inclusive ya existe
  en Supabase. **WHY**: V1-V5 ya fueron aplicadas previamente. Solo V6 (email support) y
  V7 necesitan ejecutarse en el primer deploy a Render.

- **Primer deploy**: Flyway baselines en V5 → aplica V6 → aplica V7 → app inicia normalmente.
- **Deploys subsiguientes**: Flyway encuentra todas las migraciones aplicadas, no ejecuta nada.
  La app inicia normalmente. El proceso es completamente idempotente.

### Health Checks

| Endpoint | Usado por | Auth | Descripción |
|----------|----------|------|-------------|
| `/actuator/health/liveness` | Render (deploy readiness) | Público | Liveness probe — Render lo consulta durante el deploy |
| `/actuator/health` | Monitoreo general | Público | Full health: DB (PostgreSQL) + disk space |
| `/actuator/info` | Información de build | Público | Versión, git commit, build timestamp |

Ambos endpoints de health están configurados como públicos en `SecurityConfig` — no
requieren autenticación JWT. Render usa `/actuator/health/liveness` para determinar
si el deploy fue exitoso y si el contenedor sigue respondiendo.

### Primer Deploy — Checklist

- [ ] Render Blueprint (`render.yaml`) está en `main`
- [ ] GitHub Action (`render-deploy.yml`) está en `main`
- [ ] `RENDER_DEPLOY_HOOK` secret configurado en GitHub
- [ ] Las 8 variables de entorno configuradas en Render Dashboard
- [ ] Supabase tiene `flyway_schema_history` con V1-V5 aplicadas
- [ ] Merge `develop` → `main` para disparar el deploy
- [ ] Ver logs de Render para confirmar que Flyway ejecutó V6 y V7 sin errores
- [ ] Probar: `curl https://mundolimpio-api.onrender.com/actuator/health`

### Rollback

| Escenario | Recuperación |
|-----------|-------------|
| Bad code deploy (app crashes) | `git revert` en `main` → push → GH Action dispara deploy hook → Render construye el commit revertido. Alternativa: Render Dashboard → Manual Deploy → elegir último commit conocido bueno. |
| Flyway migration failure | Rollback de imagen Docker (mismo proceso que bad code). Corregir migración en nuevo commit. Las props de baseline aseguran que Flyway no re-ejecuta migraciones ya aplicadas. |
| Env var mal configurada | Corregir en Render Dashboard. Render reinicia automáticamente con los nuevos valores — no necesita redeploy. |
| Deploy hook secret leaked | Regenerar desde Render Dashboard, actualizar GitHub Secret. La URL anterior se invalida automáticamente al regenerar. |
| Render outage | Esperar recuperación de Render. La app está lista para deploy — cuando Render vuelve, re-disparar via workflow_dispatch. |

### Troubleshooting

| Síntoma | Causa probable | Solución |
|----------|---------------|----------|
| Deploy falla, Flyway "Found non-empty schema without metadata table" | Falta `baseline-on-migrate: true` | Verificar `application-prod.yml` tiene la prop |
| Health check timeout (> 5 min) | Cold start lento de la JVM en starter plan | Aumentar health check timeout en Render Dashboard |
| Deploy hook retorna 404 | URL del hook incorrecta o expirada | Verificar y regenerar `RENDER_DEPLOY_HOOK` en GitHub Secrets |
| Errores CORS en frontend | Origins no configurados correctamente | Setear `APPLICATION_CORS_ALLOWED_ORIGINS` con la URL del frontend |
| "FATAL: remaining connection slots are reserved" | Excedido límite de 15 conexiones de Supabase free tier | Verificar HikariCP `maximum-pool-size: 5` en `application-prod.yml` |
| "This server does not support SSL" | Conexión directa a Supabase en vez del pooler | Verificar `PGHOST` usa `.pooler.supabase.com` y `PGPORT=6543` |
| Deploy hook retorna 401/403 | Secret mal configurado o hook regenerado | Regenerar hook en Render, actualizar `RENDER_DEPLOY_HOOK` en GitHub |
