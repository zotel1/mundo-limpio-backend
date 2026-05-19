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
