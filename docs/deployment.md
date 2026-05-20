> **⚠️ DEPLOYMENT MIGRATED**: This project now deploys to **Render** instead of Google Cloud Run.
> The Cloud Run content below is kept for historical reference and will be removed in a follow-up.
> See `render.yaml` and `.github/workflows/render-deploy.yml` for the current pipeline.

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

---

> **⬇️ DEPRECATED CONTENT BELOW** — Las secciones a continuación describen la
> antigua pipeline de Google Cloud Run. Se mantienen como referencia histórica
> y serán eliminadas en un PR de seguimiento. Para el deploy actual, ver la
> **Render Deployment Guide** arriba.

---

# Guía de Deploy — MundoLimpio Backend en Cloud Run

Esta guía explica cómo desplegar el backend Spring Boot de MundoLimpio en
Google Cloud Run con Supabase PostgreSQL como base de datos gestionada.

## Tabla de Contenidos

1. [Prerrequisitos](#prerrequisitos)
2. [Arquitectura de Deploy](#arquitectura-de-deploy)
3. [Configuración Inicial de GCP](#configuración-inicial-de-gcp)
4. [Supabase — Base de Datos PostgreSQL](#supabase--base-de-datos-postgresql)
5. [Artifact Registry](#artifact-registry)
6. [Cloud Build — Pipeline CI/CD](#cloud-build--pipeline-cicd)
7. [Cloud Run — Servicio Serverless](#cloud-run--servicio-serverless)
8. [Verificación del Deploy](#verificación-del-deploy)
9. [Rollback](#rollback)
10. [Diferencias Local vs Cloud Run](#diferencias-local-vs-cloud-run)
11. [Troubleshooting](#troubleshooting)

---

## Prerrequisitos

Antes de empezar, necesitás:

| Requisito | Propósito |
|-----------|-----------|
| Cuenta de Google Cloud con billing habilitado | Cloud Run, Artifact Registry y Cloud Build requieren proyecto con billing |
| Proyecto GCP creado | Todo el deploy vive dentro de un proyecto |
| `gcloud` CLI instalado y autenticado | Para ejecutar comandos desde la terminal |
| Cuenta de Supabase (free tier o plan pago) | Base de datos PostgreSQL gestionada |
| Repositorio Git conectado a Cloud Build | El pipeline se dispara con `git push` |
| Docker instalado (para prueba local) | Verificar que la imagen construye antes del deploy |

### Verificar instalación

```bash
# Verificar gcloud CLI
gcloud version
gcloud auth login

# Verificar proyecto activo
gcloud config get-value project

# Verificar Docker
docker --version
```

---

## Arquitectura de Deploy

```
┌──────────┐     git push      ┌──────────────┐    docker build    ┌────────────┐
│ Dev (Git) │ ────────────────> │ Cloud Build   │ ────────────────> │ Artifact   │
│           │                   │ (pipeline)    │                   │ Registry   │
└──────────┘                   └──────┬───────┘                   └─────┬──────┘
                                       │                                 │
                                       │ gcloud run deploy               │ pull image
                                       │                                 │
                                       ▼                                 ▼
                                ┌──────────────────────────────────────────┐
                                │            Cloud Run                     │
                                │  mundolimpio-api (us-central1)          │
                                │  CPU=1, 512Mi, max-instances=3          │
                                │  /actuator/health ← health probes       │
                                └──────────────┬───────────────────────────┘
                                               │ SSL (sslmode=require)
                                               │ Pool HikariCP max=5
                                               ▼
                                ┌──────────────────────────────────────────┐
                                │          Supabase PostgreSQL 16          │
                                │  aws-0-...pooler.supabase.com:6543      │
                                │  Free tier: max 15 conexiones           │
                                └──────────────────────────────────────────┘
```

**Componentes**:

- **Cloud Build**: Pipeline CI/CD. Compila la app con Maven, construye la imagen
  Docker con el Dockerfile multi-stage, la publica en Artifact Registry y despliega
  en Cloud Run.
- **Artifact Registry**: Registro de contenedores de GCP. Almacena las imágenes
  Docker versionadas por SHA del commit.
- **Cloud Run**: Plataforma serverless para contenedores. Escala a cero cuando
  no hay tráfico. Maneja TLS, balanceo de carga y health checks automáticamente.
- **Supabase PostgreSQL 16**: Base de datos gestionada. Conexión segura via SSL.

---

## Configuración Inicial de GCP

### 1. Crear proyecto (o usar uno existente)

```bash
gcloud projects create mundolimpio-prod \
  --name="MundoLimpio Producción" \
  --set-as-default
```

### 2. Habilitar billing

```bash
# Obtener ID de cuenta de billing
gcloud billing accounts list

# Vincular proyecto a la cuenta de billing
gcloud billing projects link mundolimpio-prod \
  --billing-account=BILLING_ACCOUNT_ID
```

### 3. Habilitar APIs necesarias

```bash
gcloud services enable \
  cloudbuild.googleapis.com \
  artifactregistry.googleapis.com \
  run.googleapis.com \
  containerregistry.googleapis.com
```

---

## Supabase — Base de Datos PostgreSQL

### 1. Crear proyecto en Supabase

1. Ir a [supabase.com/dashboard](https://supabase.com/dashboard)
2. Click en **New project**
3. Elegir organización
4. Nombre: `mundolimpio`
5. Database password: generar una segura (guardarla — es `PGPASSWORD`)
6. **Region**: elegir la más cercana a `us-central1` (ej: `us-east-1` o `us-east-2`)
7. Plan: Free (hasta 500MB database, 15 conexiones directas)

### 2. Obtener connection string

1. En Supabase dashboard, ir a **Settings > Database**
2. En la sección **Connection string**, seleccionar **URI**
3. El string tiene este formato:

   ```
   postgresql://postgres.[ref]:[password]@[host]:[port]/postgres
   ```

4. Extraer cada parte y mapearla:

   | Parte del connection string | Variable de entorno | Ejemplo |
   |----------------------------|---------------------|---------|
   | `[ref]` | (parte de PGUSER) | `abcd1234` |
   | `[password]` | `PGPASSWORD` | `tu-password-supabase` |
   | `[host]` | `PGHOST` | `aws-0-us-east-1.pooler.supabase.com` |
   | `[port]` | `PGPORT` | `6543` (PgBouncer pooler) |
   | `/postgres` | `PGDATABASE` | `postgres` |

   **IMPORTANTE**: Usar el **Pooler** (port 6543) para Cloud Run, no la conexión
   directa (port 5432). El Pooler usa PgBouncer internamente y maneja el
   connection pooling a nivel de Supabase, lo que ayuda a no exceder el límite
   de 15 conexiones del free tier.

### 3. Nota sobre Flyway

Flyway ejecuta las migraciones automáticamente al iniciar la aplicación
(`spring.flyway.enabled=true` en `application-prod.yml`). No necesitás
ejecutar migraciones manualmente. La primera vez que la app se despliega,
Flyway crea todas las tablas en Supabase.

---

## Artifact Registry

### 1. Crear repositorio de Docker

```bash
gcloud artifacts repositories create mundolimpio-repo \
  --repository-format=docker \
  --location=us-central1 \
  --description="Imágenes Docker del backend MundoLimpio"
```

### 2. Configurar autenticación para Docker local

```bash
# Solo necesario si querés hacer docker push manual
gcloud auth configure-docker us-central1-docker.pkg.dev
```

### 3. Verificar

```bash
gcloud artifacts repositories list --location=us-central1
```

Salida esperada:
```
mundolimpio-repo  DOCKER  us-central1  Imágenes Docker del backend MundoLimpio
```

---

## Cloud Build — Pipeline CI/CD

### 1. Conectar repositorio

Cloud Build necesita acceso a tu repositorio Git (GitHub, GitLab, Bitbucket o
Cloud Source Repositories).

```bash
# Para GitHub (recomendado)
# Ir a Cloud Build > Triggers > Conectar repositorio
# Autorizar a Cloud Build a acceder a tu cuenta de GitHub
# Seleccionar el repositorio: {tu-usuario}/mundo-limpio-backend
```

### 2. Crear trigger de build

```bash
gcloud builds triggers create github \
  --name="deploy-mundolimpio-api" \
  --repo-name="mundo-limpio-backend" \
  --repo-owner="TU_USUARIO_GITHUB" \
  --branch-pattern="^develop$" \
  --build-config="cloudbuild.yaml" \
  --substitutions=\
_AR_HOST=us-central1-docker.pkg.dev,\
_AR_REPO=mundolimpio-repo,\
_SERVICE_NAME=mundolimpio-api,\
_REGION=us-central1,\
_PGHOST=aws-0-us-east-1.pooler.supabase.com,\
_PGPORT=6543,\
_PGDATABASE=postgres,\
_PGUSER=postgres.TU_REF_SUPABASE,\
_PGPASSWORD=TU_PASSWORD_SUPABASE,\
_JWT_SECRET_KEY=TU_JWT_SECRET_KEY,\
_CORS_ORIGINS=*
```

**Variables de sustitución importantes**:

| Variable | Descripción | Ejemplo |
|----------|-------------|---------|
| `_AR_HOST` | Host de Artifact Registry | `us-central1-docker.pkg.dev` |
| `_AR_REPO` | Nombre del repositorio AR | `mundolimpio-repo` |
| `_SERVICE_NAME` | Nombre del servicio Cloud Run | `mundolimpio-api` |
| `_REGION` | Región de GCP | `us-central1` |
| `_PGHOST` | Host de Supabase | Pooler (port 6543) |
| `_PGPORT` | Puerto de Supabase | `6543` |
| `_PGDATABASE` | Nombre de la DB | `postgres` |
| `_PGUSER` | Usuario de Supabase | `postgres.{ref}` |
| `_PGPASSWORD` | Password de Supabase | **Secreto** |
| `_JWT_SECRET_KEY` | Clave JWT (min 32 chars) | **Secreto** |
| `_CORS_ORIGINS` | Orígenes CORS permitidos | `https://frontend.web.app` |

### 3. Probar el trigger manualmente

```bash
gcloud builds triggers run deploy-mundolimpio-api --branch=develop
```

### 4. Pipeline automático

Cada push a `develop` ejecuta el pipeline automáticamente. El flujo:

1. **Build** (5-8 min): Maven compila con dependencias cacheadas
2. **Docker build** (~2 min): Imagen JRE alpine (sin JDK)
3. **Push** (~1 min): Imagen a Artifact Registry
4. **Deploy** (~1 min): Cloud Run actualiza el servicio

Tiempo total estimado: **10-12 minutos** (primera vez, con cold Maven cache).
Siguientes builds: **5-7 minutos** (dependencias cacheadas).

---

## Cloud Run — Servicio Serverless

El servicio Cloud Run se crea automáticamente en el primer deploy del pipeline.
No necesitás crearlo manualmente.

### Configuración aplicada automáticamente

| Parámetro | Valor | Justificación |
|-----------|-------|---------------|
| CPU | 1 vCPU | Suficiente para endpoints CRUD |
| Memoria | 512 MiB | Heap ~384Mi + overhead |
| Concurrencia | 80 | Por instancia |
| Timeout | 300s | 5 min máximo por request |
| Min instancias | 0 | Sin tráfico = sin costo |
| Max instancias | 3 | 3×5 pool = 15 (límite Supabase) |
| Puerto | 8080 | Puerto por defecto de Spring Boot |
| Health check | `/actuator/health` | Endpoint desprotegido |

### Configurar variables de entorno manualmente

Si necesitás cambiar variables de entorno sin re-deployar:

1. Ir a **Cloud Run > mundolimpio-api > Edit & Deploy New Revision**
2. En la sección **Variables & Secrets > Variables**
3. Agregar o modificar las variables necesarias
4. Click **Deploy**

### URL del servicio

Después del primer deploy, Cloud Run asigna una URL:

```
https://mundolimpio-api-xxxxxxxxx-uc.a.run.app
```

Para ver la URL:

```bash
gcloud run services describe mundolimpio-api \
  --region=us-central1 \
  --format="value(status.url)"
```

---

## Verificación del Deploy

### 1. Health check

```bash
# Verificar que la app responde
curl https://mundolimpio-api-xxxxxxxxx-uc.a.run.app/actuator/health
```

Respuesta esperada:
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    }
  }
}
```

**IMPORTANTE**: Si `db.status` es `"DOWN"`, la conexión a Supabase falló.
Revisar las variables de entorno en Cloud Run (especialmente `PGHOST` y `PGPASSWORD`).

### 2. Smoke test — endpoint público de productos

```bash
# GET productos (endpoint público sin autenticación)
curl https://mundolimpio-api-xxxxxxxxx-uc.a.run.app/api/v1/product
```

Si la base de datos está vacía, la respuesta es `[]` (array vacío). Si hay datos,
devuelve la lista de productos.

### 3. Verificar logs en tiempo real

```bash
# Ver logs del servicio Cloud Run
gcloud run services logs tail mundolimpio-api --region=us-central1

# Ver logs de Cloud Build (último build)
gcloud builds log $(gcloud builds list --limit=1 --format="value(id)")
```

### 4. Verificar revisiones activas

```bash
gcloud run revisions list \
  --service=mundolimpio-api \
  --region=us-central1
```

Muestra todas las revisiones desplegadas. La última con `SERVING` al 100% es la activa.

---

## Rollback

Si una nueva versión causa problemas, volver a la revisión anterior:

### Opción 1: Desde la consola de Cloud Run

1. Ir a **Cloud Run > mundolimpio-api > Revisions**
2. Encontrar la revisión anterior que funcionaba
3. Click en los tres puntos `...` > **Manage traffic**
4. Asignar 100% del tráfico a esa revisión

### Opción 2: Desde CLI con gcloud

```bash
# Listar revisiones para encontrar la anterior
gcloud run revisions list \
  --service=mundolimpio-api \
  --region=us-central1

# Redirigir tráfico a una revisión específica
gcloud run services update-traffic mundolimpio-api \
  --to-revisions=mundolimpio-api-00002-abc=100 \
  --region=us-central1
```

### Opción 3: Re-deployar una imagen anterior

```bash
# Listar imágenes en Artifact Registry
gcloud artifacts docker images list \
  us-central1-docker.pkg.dev/mundolimpio-prod/mundolimpio-repo/mundolimpio-api

# Deployar una imagen específica por SHA
gcloud run deploy mundolimpio-api \
  --image=us-central1-docker.pkg.dev/mundolimpio-prod/mundolimpio-repo/mundolimpio-api:abc1234 \
  --region=us-central1
```

---

## Diferencias Local vs Cloud Run

### Desarrollo local (Docker Compose)

| Aspecto | Configuración |
|---------|--------------|
| Base de datos | PostgreSQL 16 local (contenedor `postgres:16-alpine`) |
| URL JDBC | `jdbc:postgresql://postgres_db:5432/${DB_NAME}` |
| SSL | **Deshabilitado** (contenedor local sin SSL) |
| Env vars | `.env` en raíz del proyecto |
| Puerto | `localhost:8080` |
| Hot reload | Con `spring-boot-devtools` |
| Perfil Spring | `prod` (compartido) |

### Producción (Cloud Run + Supabase)

| Aspecto | Configuración |
|---------|--------------|
| Base de datos | Supabase PostgreSQL 16 (gestionado) |
| URL JDBC | `jdbc:postgresql://${PGHOST}:${PGPORT}/${PGDATABASE}` |
| SSL | **Requerido** (`ssl=true&sslmode=require`) |
| Env vars | Cloud Run UI o `--update-env-vars` en deploy |
| Puerto | `8080` (Cloud Run asigna automáticamente) |
| Hot reload | No aplica |
| Perfil Spring | `prod` |

### Variables de entorno mapeadas

| Propósito | Desarrollo local (`.env`) | Producción (Cloud Run) |
|----------|--------------------------|------------------------|
| Host PostgreSQL | N/A (usa `postgres_db` en URL) | `PGHOST` |
| Puerto PostgreSQL | N/A (usa `5432` en URL) | `PGPORT` |
| Nombre DB | `DB_NAME` | `PGDATABASE` |
| Usuario | `DB_USER` | `PGUSER` |
| Password | `DB_PASSWORD` | `PGPASSWORD` |
| JWT Secret | `JWT_SECRET_KEY` | `JWT_SECRET_KEY` |
| CORS Orígenes | `APPLICATION_CORS_ALLOWED_ORIGINS` | `CORS_ORIGINS` |

**IMPORTANTE**: Docker Compose local sobrescribe `spring.datasource.url` via
`SPRING_DATASOURCE_URL=jdbc:postgresql://postgres_db:5432/${DB_NAME}`, por lo que
las propiedades SSL de `application-prod.yml` no afectan el desarrollo local.

---

## Troubleshooting

### Error: "Este servidor no soporta SSL"

**Causa**: `application-prod.yml` configura `ssl=true` y `sslmode=require`, pero
la base de datos no tiene SSL habilitado.

**Solución**:
- **En Cloud Run**: Verificar que `PGHOST` apunta al Pooler de Supabase (`.pooler.supabase.com`),
  no a la conexión directa.
- **En desarrollo local**: Esto no debería pasar porque Docker Compose sobrescribe
  la URL del datasource. Si pasa, verificar `SPRING_DATASOURCE_URL` en `docker-compose.yml`.

### Error: "FATAL: remaining connection slots are reserved"

**Causa**: Se excedió el límite de 15 conexiones del free tier de Supabase.

**Solución**:
- Verificar que `max-instances` en Cloud Run es ≤ 3.
- Verificar que `spring.datasource.hikari.maximum-pool-size` es 5 en `application-prod.yml`.
- Si el problema persiste, considerar:
  - Usar PgBouncer de Supabase (port 6543)
  - Reducir `maximum-pool-size` a 3 o 4
  - Hacer upgrade al plan Pro de Supabase

### Error: "Cloud Run: Ready condition status: False"

**Causa**: El health check de Cloud Run está fallando. El contenedor arranca pero
`/actuator/health` no responde 200.

**Solución**:
1. Verificar logs: `gcloud run services logs tail mundolimpio-api --region=us-central1`
2. Causas comunes:
   - **Flyway falló**: Error en migraciones. Verificar que las migraciones son compatibles
     con PostgreSQL 16 (antes usábamos MySQL — verificar sintaxis de DDL).
   - **Datasource no conecta**: Error en `PGHOST`, `PGPORT` o `PGPASSWORD`.
   - **Timeout**: La app tarda más de 5 minutos en arrancar. Aumentar timeout en `service.yaml`.
3. Localmente: `docker-compose up` y verificar que `curl localhost:8080/actuator/health` responde 200.

### Error: "Cloud Build: Permission denied accessing Artifact Registry"

**Causa**: La service account de Cloud Build no tiene permisos para escribir en
Artifact Registry.

**Solución**:

```bash
# Obtener la service account de Cloud Build
PROJECT_NUMBER=$(gcloud projects describe mundolimpio-prod --format="value(projectNumber)")

# Dar permiso de escritura en Artifact Registry
gcloud artifacts repositories add-iam-policy-binding mundolimpio-repo \
  --location=us-central1 \
  --member="serviceAccount:${PROJECT_NUMBER}@cloudbuild.gserviceaccount.com" \
  --role="roles/artifactregistry.writer"
```

### Error: "This container image doesn't support the specified health check endpoint"

**Causa**: Cloud Run no encuentra el endpoint `/actuator/health`. Posiblemente
la app está usando JDK runtime sin Actuator en el classpath.

**Solución**:
- Verificar que `spring-boot-starter-actuator` está en `pom.xml`
- Verificar que `SecurityConfig` permite `/actuator/health` sin autenticación
- Verificar que `application-prod.yml` tiene `management.endpoints.web.exposure.include: health,info`

### Cold start lento (> 5 segundos)

**Causa**: La JVM necesita tiempo para inicializar en el primer request después
de escalar a cero.

**Solución**:
- La imagen JRE alpine ya minimiza el cold start (~3-5s)
- Para reducirlo más: `--min-instances=1` (pero incurre costo mínimo)
- Class Data Sharing (CDS) puede reducir cold start — evaluar para futura iteración

---

## Referencias

- [Cloud Run Documentation](https://cloud.google.com/run/docs)
- [Cloud Build Documentation](https://cloud.google.com/build/docs)
- [Artifact Registry Documentation](https://cloud.google.com/artifact-registry/docs)
- [Supabase Documentation](https://supabase.com/docs)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Dockerfile reference](Dockerfile) — Dockerfile multi-stage del proyecto
- [cloudbuild.yaml](cloudbuild.yaml) — Pipeline CI/CD
- [service.yaml](service.yaml) — Especificación del servicio Cloud Run
- [.env.example](.env.example) — Variables de entorno para desarrollo local
