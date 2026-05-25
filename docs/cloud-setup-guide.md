# Cloud Setup Guide — MundoLimpio Backend

Guía paso a paso para configurar Supabase (PostgreSQL 16) y Google Cloud Platform
(Cloud Run + Cloud Build + Artifact Registry) desde cero con la cuenta
`mundolimpio.app@gmail.com`.

**IMPORTANTE**: Todos los valores de conexión, contraseñas y secretos en esta
guía son placeholders. Reemplazalos con los valores reales de tu cuenta.

---

## Tabla de Contenidos

1. [Parte 1: Supabase — Base de Datos PostgreSQL](#parte-1-supabase--base-de-datos-postgresql)
2. [Parte 2: Google Cloud Platform](#parte-2-google-cloud-platform)
3. [Parte 3: Seguridad](#parte-3-seguridad)
4. [Referencia Rápida — Mapa de Variables](#referencia-rápida--mapa-de-variables)

---

## Parte 1: Supabase — Base de Datos PostgreSQL

### Paso 1.1 — Crear proyecto en Supabase

1. Abrí [supabase.com/dashboard](https://supabase.com/dashboard) e iniciá sesión
   con `mundolimpio.app@gmail.com`.
2. Click en **New project**.
3. Completá el formulario:

   | Campo | Valor |
   |-------|-------|
   | Organization | La que aparezca por defecto (o creá una nueva) |
   | Name | `mundolimpio` |
   | Database Password | **Generá una fuerte** (min 12 chars, guardala — es `PGPASSWORD`) |
   | Region | `us-east-1` (Ohio) — la más cercana a `us-central1` de GCP |
   | Plan | **Free** (500 MB DB, 15 conexiones directas, 2 GB bandwidth) |

4. Click en **Create project**. Esperá ~2 minutos a que la base de datos se
   aprovisione.

### Paso 1.2 — Encontrar el connection string de PostgreSQL

1. En el dashboard de Supabase, andá a **Settings** (ícono de engranaje, abajo
   a la izquierda).
2. En el menú lateral, click en **Database**.
3. En la sección **Connection string**, seleccioná la pestaña **URI**.
4. Vas a ver un string con este formato:

   ```
   postgresql://postgres.[ref]:[password]@aws-0-us-east-1.pooler.supabase.com:6543/postgres
   ```

   **ATENCIÓN**: Hay **dos** connection strings — **Transaction** (pooler, port
   6543) y **Session** (direct, port 5432). Para Cloud Run **SIEMPRE usá el
   Pooler** (port 6543). El pooler usa PgBouncer internamente y evita saturar
   las 15 conexiones del free tier.

### Paso 1.3 — Mapear partes del connection string a nuestras env vars

Tomá el connection string y extraé cada parte:

| Parte del URI | Valor de ejemplo | Variable de entorno | Dónde se usa |
|---|---|---|---|
| `postgres.[ref]` | `postgres.abc123def456` | `PGUSER` | `application-prod.yml` y `cloudbuild.yaml` |
| `[password]` | `tu-password-supabase` | `PGPASSWORD` | `application-prod.yml` y `cloudbuild.yaml` |
| `aws-0-us-east-1.pooler.supabase.com` | (ese mismo) | `PGHOST` | `application-prod.yml` y `cloudbuild.yaml` |
| `6543` | (ese mismo) | `PGPORT` | `application-prod.yml` y `cloudbuild.yaml` |
| `postgres` | (ese mismo) | `PGDATABASE` | `application-prod.yml` y `cloudbuild.yaml` |

Ejemplo de cómo se ven los valores extraídos:

```
PGHOST=aws-0-us-east-1.pooler.supabase.com
PGPORT=6543
PGDATABASE=postgres
PGUSER=postgres.abc123def456
PGPASSWORD=tu-password-supabase
```

### Paso 1.4 — SSL (ya configurado en el proyecto)

Nuestra configuración de HikariCP en `application-prod.yml:28-29` ya incluye:

```yaml
data-source-properties:
  ssl: "true"
  sslmode: require
```

Supabase **requiere** SSL en todas las conexiones externas. Si ves el error
`Este servidor no soporta SSL` o `FATAL: no pg_hba.conf entry`, verificá que
estás usando el host del Pooler (`.pooler.supabase.com`), no la conexión
directa (`db.xxxxx.supabase.co`).

### Paso 1.5 — Ejecutar el script de migración contra Supabase

El script `scripts/migrate-db.sh` migra datos desde una base MySQL existente
a PostgreSQL. Usa sus propias variables de entorno:

```bash
# Setear variables para Supabase
export PG_HOST=aws-0-us-east-1.pooler.supabase.com
export PG_PORT=6543
export PG_USER=postgres.abc123def456
export PG_PASSWORD=tu-password-supabase
export PG_DATABASE=postgres

# Setear variables para MySQL (si tenés datos que migrar)
export MYSQL_HOST=tu-host-mysql
export MYSQL_PORT=3306
export MYSQL_USER=root
export MYSQL_PASSWORD=tu-password-mysql
export MYSQL_DATABASE=mundolimpio

# Dry-run (solo exporta + transforma, no importa)
bash scripts/migrate-db.sh --dry-run

# Migración completa
bash scripts/migrate-db.sh
```

**Nota sobre Flyway**: La aplicación ejecuta las migraciones de Flyway
automáticamente al iniciar (`flyway.enabled=true` en `application-prod.yml`).
No necesitás crear tablas manualmente. La primera vez que la app se despliega
en Cloud Run, Flyway ejecuta `V1__Initial_Schema.sql` → `V4__Update_...`.

---

## Parte 2: Google Cloud Platform

### Paso 2.1 — Crear proyecto GCP

```bash
# 1. Autenticarse con la cuenta de Google
gcloud auth login mundolimpio.app@gmail.com

# 2. Crear el proyecto
gcloud projects create PROJECT_ID \
  --name="MundoLimpio" \
  --set-as-default

# 3. Habilitar billing (REQUERIDO para Cloud Run y Cloud Build)
#    Primero, obtené tu BILLING_ACCOUNT_ID:
gcloud billing accounts list

#    Después vinculá el proyecto:
gcloud billing projects link PROJECT_ID \
  --billing-account=BILLING_ACCOUNT_ID
```

> **Reemplazá `PROJECT_ID`** con el ID de proyecto que elegiste
> (ej: `mundolimpio-prod`). Anotalo — lo vas a usar en todos los comandos
> siguientes.

### Paso 2.2 — Habilitar las APIs necesarias

```bash
gcloud services enable \
  run.googleapis.com \
  cloudbuild.googleapis.com \
  artifactregistry.googleapis.com
```

Verificá que se habilitaron correctamente:

```bash
gcloud services list --enabled --filter="name:run.googleapis.com OR name:cloudbuild.googleapis.com OR name:artifactregistry.googleapis.com"
```

### Paso 2.3 — Crear repositorio en Artifact Registry

Artifact Registry almacena las imágenes Docker que construye Cloud Build.

```bash
gcloud artifacts repositories create mundolimpio-repo \
  --repository-format=docker \
  --location=us-central1 \
  --description="Imágenes Docker del backend MundoLimpio"
```

Verificá:

```bash
gcloud artifacts repositories list --location=us-central1
```

Salida esperada:

```
mundolimpio-repo  DOCKER  us-central1  Imágenes Docker del backend MundoLimpio
```

La ruta completa de tu repositorio será:

```
us-central1-docker.pkg.dev/PROJECT_ID/mundolimpio-repo
```

### Paso 2.4 — Conectar repositorio GitHub a Cloud Build

1. Abrí [console.cloud.google.com/cloud-build/triggers](https://console.cloud.google.com/cloud-build/triggers)
2. Seleccioná tu proyecto (`PROJECT_ID`).
3. Click en **Connect Repository**.
4. Elegí **GitHub (Cloud Build GitHub App)**.
5. Autorizá a Cloud Build a acceder a tu cuenta de GitHub.
6. Seleccioná el repositorio `TU_USUARIO/mundo-limpio-backend`.
7. Click en **Connect**.

### Paso 2.5 — Crear trigger de Cloud Build

El trigger escucha pushes a `develop` y ejecuta el pipeline definido en
`cloudbuild.yaml`.

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
_PGUSER=postgres.abc123def456,\
_PGPASSWORD=tu-password-supabase,\
_JWT_SECRET_KEY=tu-jwt-secret-min-256-bits,\
_CORS_ORIGINS=*
```

**Explicación de cada variable de sustitución**:

| Variable | Dónde se obtiene | Ejemplo |
|----------|-----------------|---------|
| `_AR_HOST` | Fijo | `us-central1-docker.pkg.dev` |
| `_AR_REPO` | Paso 2.3 | `mundolimpio-repo` |
| `_SERVICE_NAME` | Elegí uno | `mundolimpio-api` |
| `_REGION` | Elegí uno | `us-central1` |
| `_PGHOST` | Supabase → Paso 1.2 | `aws-0-us-east-1.pooler.supabase.com` |
| `_PGPORT` | Supabase → Pooler | `6543` |
| `_PGDATABASE` | Supabase → Fijo | `postgres` |
| `_PGUSER` | Supabase → Paso 1.3 | `postgres.abc123def456` |
| `_PGPASSWORD` | Supabase → contraseña del proyecto | `tu-password-supabase` |
| `_JWT_SECRET_KEY` | Generar una nueva | min 32 caracteres aleatorios |
| `_CORS_ORIGINS` | URL del frontend o `*` | `https://miapp.web.app` |

### Paso 2.6 — Generar JWT_SECRET_KEY

```bash
# En Linux/macOS:
openssl rand -base64 64

# En PowerShell (Windows):
[Convert]::ToBase64String((1..64 | ForEach-Object { Get-Random -Maximum 256 }))
```

Usá el resultado como `_JWT_SECRET_KEY` en el trigger y `JWT_SECRET_KEY` en las
variables de entorno.

### Paso 2.7 — Primer deploy

El servicio Cloud Run se crea **automáticamente** en el primer deploy. No
necesitás crearlo manualmente.

Dispará el trigger por primera vez:

```bash
gcloud builds triggers run deploy-mundolimpio-api --branch=develop
```

Tiempo estimado del primer build: **10-12 minutos** (descarga de dependencias
Maven + build de imagen + deploy). Builds siguientes: **5-7 minutos** (con
cache de Maven).

### Paso 2.8 — Verificar el deploy

```bash
# 1. Obtener la URL del servicio
gcloud run services describe mundolimpio-api \
  --region=us-central1 \
  --format="value(status.url)"

# 2. Health check
curl https://mundolimpio-api-xxxxxxxxx-uc.a.run.app/actuator/health

# 3. Ver logs en tiempo real
gcloud run services logs tail mundolimpio-api --region=us-central1
```

Respuesta esperada del health check:

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

Si `db.status` es `"DOWN"`, revisá las variables de entorno en Cloud Run
(especialmente `PGHOST` y `PGPASSWORD`).

### Paso 2.9 — Configurar variables de entorno en Cloud Run (post-deploy)

Si necesitás cambiar variables **sin re-deployar** (por ejemplo, rotar la
JWT_SECRET_KEY):

```bash
gcloud run services update mundolimpio-api \
  --region=us-central1 \
  --update-env-vars="^:^PGHOST=aws-0-us-east-1.pooler.supabase.com:6543" \
  --update-env-vars="PGPORT=6543" \
  --update-env-vars="PGDATABASE=postgres" \
  --update-env-vars="PGUSER=postgres.abc123def456" \
  --update-env-vars="PGPASSWORD=tu-password-supabase" \
  --update-env-vars="JWT_SECRET_KEY=tu-jwt-secret-min-256-bits" \
  --update-env-vars="SPRING_PROFILES_ACTIVE=prod" \
  --update-env-vars="APPLICATION_CORS_ALLOWED_ORIGINS=*"
```

O desde la consola web: **Cloud Run > mundolimpio-api > Edit & Deploy New
Revision > Variables & Secrets**.

### Paso 2.10 — IAM Permisos necesarios

La service account de Cloud Build necesita permisos para:

1. **Escribir en Artifact Registry** (pushear imágenes):

```bash
PROJECT_NUMBER=$(gcloud projects describe PROJECT_ID --format="value(projectNumber)")

gcloud artifacts repositories add-iam-policy-binding mundolimpio-repo \
  --location=us-central1 \
  --member="serviceAccount:${PROJECT_NUMBER}@cloudbuild.gserviceaccount.com" \
  --role="roles/artifactregistry.writer"
```

2. **Desplegar en Cloud Run** (el comando `gcloud run deploy`):

```bash
gcloud projects add-iam-policy-binding PROJECT_ID \
  --member="serviceAccount:${PROJECT_NUMBER}@cloudbuild.gserviceaccount.com" \
  --role="roles/run.admin"

gcloud projects add-iam-policy-binding PROJECT_ID \
  --member="serviceAccount:${PROJECT_NUMBER}@cloudbuild.gserviceaccount.com" \
  --role="roles/iam.serviceAccountUser"
```

3. **Leer secretos de Secret Manager** (ver Parte 3):

```bash
gcloud projects add-iam-policy-binding PROJECT_ID \
  --member="serviceAccount:${PROJECT_NUMBER}@cloudbuild.gserviceaccount.com" \
  --role="roles/secretmanager.secretAccessor"
```

---

## Parte 3: Seguridad

### Paso 3.1 — Mover secretos a Secret Manager

Las variables sensibles (`PGPASSWORD`, `JWT_SECRET_KEY`) no deberían estar
como texto plano en las sustituciones del trigger. Secret Manager las cifra en
reposo y las inyecta como variables de entorno en Cloud Run.

#### 3.1.1 — Crear los secretos

```bash
# Crear secreto para la contraseña de Supabase
echo -n "tu-password-supabase" | gcloud secrets create mundolimpio-pgpassword \
  --replication-policy="automatic" \
  --data-file=-

# Crear secreto para la clave JWT
echo -n "tu-jwt-secret-min-256-bits" | gcloud secrets create mundolimpio-jwt-secret \
  --replication-policy="automatic" \
  --data-file=-
```

#### 3.1.2 — Dar acceso a Cloud Run

```bash
# Obtener la service account de Cloud Run
# (por defecto es la compute service account)
COMPUTE_SA="${PROJECT_NUMBER}-compute@developer.gserviceaccount.com"

gcloud secrets add-iam-policy-binding mundolimpio-pgpassword \
  --member="serviceAccount:${COMPUTE_SA}" \
  --role="roles/secretmanager.secretAccessor"

gcloud secrets add-iam-policy-binding mundolimpio-jwt-secret \
  --member="serviceAccount:${COMPUTE_SA}" \
  --role="roles/secretmanager.secretAccessor"
```

#### 3.1.3 — Referenciar secretos en Cloud Run

Desde la consola de Cloud Run (**Edit & Deploy New Revision > Variables &
Secrets**):

1. En la sección **Secrets**, click en **Add Secret**.
2. Completá:

   | Secret | Versión | Método | Variable de entorno |
   |--------|---------|--------|-------------------|
   | `mundolimpio-pgpassword` | `latest` | Exposed as environment variable | `PGPASSWORD` |
   | `mundolimpio-jwt-secret` | `latest` | Exposed as environment variable | `JWT_SECRET_KEY` |

O desde CLI:

```bash
gcloud run deploy mundolimpio-api \
  --region=us-central1 \
  --image=us-central1-docker.pkg.dev/PROJECT_ID/mundolimpio-repo/mundolimpio-api:latest \
  --set-secrets="PGPASSWORD=mundolimpio-pgpassword:latest" \
  --set-secrets="JWT_SECRET_KEY=mundolimpio-jwt-secret:latest" \
  --update-env-vars="PGHOST=aws-0-us-east-1.pooler.supabase.com" \
  --update-env-vars="PGPORT=6543" \
  --update-env-vars="PGDATABASE=postgres" \
  --update-env-vars="PGUSER=postgres.abc123def456" \
  --update-env-vars="SPRING_PROFILES_ACTIVE=prod" \
  --update-env-vars="APPLICATION_CORS_ALLOWED_ORIGINS=*"
```

Una vez configurados los secretos, podés **eliminar** `PGPASSWORD` y
`JWT_SECRET_KEY` de las variables de sustitución en el trigger de Cloud Build
(en `cloudbuild.yaml`, comentar o dejar los placeholders con `CHANGE_ME`).

### Paso 3.2 — Agregar entradas al .gitignore

El archivo `.gitignore` actual ya cubre `.env`. Agregá las siguientes líneas
para proteger credenciales de GCP y claves:

```gitignore
# Google Cloud credentials y claves
*.pem
service-account-key.json
credentials/

# Archivos de entorno (nunca commitear)
.env

# Session files
session-*.md
```

Verificá que estas entradas estén presentes y **no** estén comentadas:

```bash
# Ver qué entradas de gitignore se aplican
git check-ignore .env
git check-ignore credentials/test.json
git check-ignore service-account-key.json
```

Si `git check-ignore` **no devuelve** la ruta del archivo como salida,
significa que el archivo no está siendo ignorado. Revisá `.gitignore`.

### Paso 3.3 — Verificar que no haya secretos en el historial de git

```bash
# Buscar contraseñas o tokens en el historial
git log -p --all --full-history -- . | rg -i "(password|secret|token|key)\s*="

# Buscar archivos sensibles que se hayan commiteado
git log --all --full-history -- "*.pem" "*.env" "credentials/**" "**/service-account*.json"

# Buscar strings que parezcan connection strings
git log -p --all --full-history -- . | rg "postgresql://"
```

Si encontrás algo, corregilo **inmediatamente**:

```bash
# Opción A: Si es un commit reciente y no fue pusheado
git reset --soft HEAD~1
# Eliminá el archivo/secreto del staging, volvé a commitear

# Opción B: Si ya fue pusheado, usá BFG Repo-Cleaner o git filter-branch
# y rotá todas las credenciales expuestas
```

**Regla de oro**: Si un secreto aparece en git history aunque sea una vez,
consideralo comprometido. **Rotá las credenciales** (generá nueva password de
Supabase, nueva JWT_SECRET_KEY).

---

## Referencia Rápida — Mapa de Variables

| Variable | Fuente | En desarrollo local | En Cloud Run / Cloud Build |
|----------|--------|-------------------|--------------------------|
| `PGHOST` | Supabase Pooler host | No se usa (docker-compose) | `aws-0-us-east-1.pooler.supabase.com` |
| `PGPORT` | Supabase port | No se usa | `6543` |
| `PGDATABASE` | Supabase db name | `DB_NAME` en `.env` | `postgres` |
| `PGUSER` | Supabase user | `DB_USER` en `.env` | `postgres.{ref}` |
| `PGPASSWORD` | Supabase password | `DB_PASSWORD` en `.env` | Secret Manager o trigger substitution |
| `JWT_SECRET_KEY` | Generada manualmente | `.env` | Secret Manager o trigger substitution |
| `SPRING_PROFILES_ACTIVE` | Fijo | `prod` (vía docker-compose) | `prod` |
| `CORS_ORIGINS` | URL del frontend | `.env` | `*` o URL del deploy de Flutter |

---

## Comandos Útiles para el Día a Día

```bash
# Ver estado del servicio
gcloud run services describe mundolimpio-api --region=us-central1

# Ver URL del servicio
gcloud run services describe mundolimpio-api \
  --region=us-central1 --format="value(status.url)"

# Ver revisiones (historial de deploys)
gcloud run revisions list --service=mundolimpio-api --region=us-central1

# Rollback a revisión anterior
gcloud run services update-traffic mundolimpio-api \
  --to-revisions=mundolimpio-api-00002-abc=100 \
  --region=us-central1

# Ver logs del build más reciente
gcloud builds log $(gcloud builds list --limit=1 --format="value(id)")

# Listar imágenes en Artifact Registry
gcloud artifacts docker images list \
  us-central1-docker.pkg.dev/PROJECT_ID/mundolimpio-repo/mundolimpio-api

# Ver logs de la app en tiempo real
gcloud run services logs tail mundolimpio-api --region=us-central1
```

---

## Troubleshooting Rápido

| Error | Causa Probable | Solución |
|-------|---------------|----------|
| `FATAL: remaining connection slots are reserved` | Límite de 15 conexiones del free tier excedido | Reducí `max-instances` a 3 o bajá `maximum-pool-size` a 3 |
| `Este servidor no soporta SSL` | Estás usando host directo en vez del Pooler | Usá `.pooler.supabase.com` en `PGHOST` |
| `Permission denied accessing Artifact Registry` | Cloud Build SA sin permisos de escritura | Ejecutá Paso 2.10, punto 1 |
| `Cloud Run: Ready condition status: False` | Health check `/actuator/health` falla | Revisá logs, verificá conexión a Supabase, verificá migraciones Flyway |
| Cold start > 5 segundos | JVM inicializando después de scale-to-zero | Aceptable para MVP; en producción usar `--min-instances=1` |

---

## Recursos Relacionados

- [cloudbuild.yaml](../cloudbuild.yaml) — Pipeline CI/CD
- [service.yaml](../service.yaml) — Especificación del servicio Cloud Run (referencia)
- [deployment.md](deployment.md) — Guía de deploy general
- [.env.example](../.env.example) — Variables de entorno para desarrollo local
- [application-prod.yml](../src/main/resources/application-prod.yml) — Configuración de Spring Boot para producción
- [Supabase Docs](https://supabase.com/docs)
- [Cloud Run Docs](https://cloud.google.com/run/docs)
- [Cloud Build Docs](https://cloud.google.com/build/docs)
- [Artifact Registry Docs](https://cloud.google.com/artifact-registry/docs)
- [Secret Manager Docs](https://cloud.google.com/secret-manager/docs)
