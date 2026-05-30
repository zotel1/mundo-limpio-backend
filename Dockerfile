# Etapa 1: build (Maven + JDK 21)
# WHAT: Compila la app Spring Boot usando Maven con JDK completo
# WHY: Necesitamos JDK para compilar, pero NO lo llevamos a la imagen final
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Copiamos pom.xml primero para cachear las dependencias Maven
# WHAT: Capa de dependencias independiente del código fuente
# WHY: Si solo cambian src/ y no pom.xml, esta capa se reusa del cache
COPY pom.xml .
RUN mvn dependency:go-offline

COPY src ./src
RUN mvn clean package -DskipTests

# Etapa 2: runtime (JRE 21 alpine, sin JDK completo)
# WHAT: Imagen mínima solo con JRE (Java Runtime Environment) en Alpine Linux
# WHY: Reduce ~80MB vs JDK completo. Alpine es más liviana (~5MB base vs ~70MB Ubuntu).
#       Para Cloud Run, cada MB cuenta — menor imagen = menor cold start.
# DIFFERENCES: Antes usábamos eclipse-temurin:21-jdk (~420MB imagen final).
#              Ahora eclipse-temurin:21-jre-alpine (~180MB imagen final esperada).
FROM eclipse-temurin:21-jre-alpine

# WHAT: Instalar Tesseract OCR con language pack español (spa).
# WHY: tesseract-ocr es el motor OCR; tesseract-ocr-data-spa contiene
#      el modelo de idioma español necesario para leer tickets argentinos.
#      Sin el language pack, Tesseract solo funciona en inglés.
RUN apk add --no-cache tesseract-ocr tesseract-ocr-data-spa

# WHAT: Instalar PostgreSQL client para pg_dump (backups manuales de BD)
# WHY: El endpoint de backup ejecuta pg_dump via ProcessBuilder para generar
#      un dump completo de la base de datos y subirlo a Supabase Storage.
RUN apk add --no-cache postgresql-client

# Crear usuario no-root para seguridad
# WHAT: Usuario 'cloudrun' con UID 1001, sin privilegios de root
# WHY: Cloud Run recomienda no-root por seguridad. Si un atacante explota la app,
#      no obtiene acceso root al contenedor. UID 1001 es convención de Google.
# HOW: addgroup + adduser (Alpine), creamos el grupo y usuario en un solo RUN
RUN addgroup -S cloudrun && adduser -S cloudrun -G cloudrun

WORKDIR /app

# Copiar solo el JAR compilado desde la etapa builder
COPY --from=builder /app/target/*.jar app.jar

# EXPOSE debe ir ANTES de USER — Cloud Run inyecta el PORT env var
# WHAT: Puerto 8080 expuesto (Cloud Run por defecto)
# WHY: EXPOSE antes de USER evita problemas de permisos en algunos runtimes
EXPOSE 8080

# Cambiar a usuario no-root DESPUÉS de EXPOSE
USER cloudrun

# JVM flags optimizados para contenedores
# WHAT: Flags que ajustan la JVM al entorno de contenedor (Cloud Run)
# WHY:
#   -XX:+UseContainerSupport: Detecta límites de cgroup (CPU/memoria) del
#      contenedor en vez de la máquina host. Sin esto, la JVM ve 64GB del host
#      y reserva memoria de más, causando OOMKilled en Cloud Run.
#   -XX:MaxRAMPercentage=75.0: Limita el heap al 75% de la memoria del
#      contenedor. Con 512Mi en Cloud Run, heap ~384Mi, dejando ~128Mi para
#      Metaspace, threads, y I/O buffers. Sin esto, la JVM podría usar toda
#      la memoria y el contenedor sería matado por OOM.
#   -Djava.security.egd=file:/dev/./urandom: Acelera la inicialización de
#      SecureRandom en Alpine (que no tiene /dev/random rápido por defecto).
#      Sin esto, Tomcat puede tardar 30-60s en arrancar esperando entropía.
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dserver.port=${PORT:8080}", \
  "-Dspring.profiles.active=prod", \
  "-jar", "app.jar"]
