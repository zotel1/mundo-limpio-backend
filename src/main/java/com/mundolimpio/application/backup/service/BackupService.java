package com.mundolimpio.application.backup.service;

import com.mundolimpio.application.backup.domain.Backup;
import com.mundolimpio.application.backup.domain.BackupStatus;
import com.mundolimpio.application.backup.dto.BackupResponse;
import com.mundolimpio.application.backup.repository.BackupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.zip.GZIPOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * WHAT: Servicio principal de backups de base de datos.
 * WHY: Ejecuta pg_dump via ProcessBuilder, comprime con gzip, sube a Supabase Storage (S3)
 *      y persiste la metadata en la tabla backups.
 *      Solo accesible por usuarios ADMIN via BackupController.
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    private final BackupRepository repository;
    private final S3Client s3Client;
    private final String backupBucket;
    private final String publicBaseUrl;
    private final String supabaseEndpoint;

    public BackupService(
            BackupRepository repository,
            S3Client s3Client,
            @Value("${app.backup.bucket:backups}") String backupBucket,
            @Value("${app.supabase.public-base-url}") String publicBaseUrl,
            @Value("${app.supabase.endpoint}") String supabaseEndpoint) {
        this.repository = repository;
        this.s3Client = s3Client;
        this.backupBucket = backupBucket;
        this.publicBaseUrl = publicBaseUrl;
        this.supabaseEndpoint = supabaseEndpoint;
    }

    /**
     * WHAT: Ejecuta pg_dump, comprime con gzip, sube a S3, guarda metadata.
     * WHY: Backup manual completo de la BD para el rol ADMIN (BKP-001).
     *
     * @return BackupResponse con metadata del backup creado
     * @throws RuntimeException si pg_dump falla o S3 no esta disponible
     */
    @Transactional
    public BackupResponse createBackup() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String filename = "mundolimpio-" + timestamp + ".sql.gz";
        String s3Key = "backups/" + filename;

        try {
            // 1. Ejecutar pg_dump
            log.info("Starting pg_dump for backup: {}", filename);
            byte[] uncompressedBytes = executePgDump();
            log.info("pg_dump completed: {} bytes uncompressed", uncompressedBytes.length);

            // 2. Comprimir con gzip
            ByteArrayOutputStream compressedBaos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzipOut = new GZIPOutputStream(compressedBaos)) {
                gzipOut.write(uncompressedBytes);
                gzipOut.finish();
            }
            byte[] compressedBytes = compressedBaos.toByteArray();
            double ratio = uncompressedBytes.length > 0
                    ? (double) compressedBytes.length / uncompressedBytes.length * 100
                    : 0;
            log.info("Compressed to {} bytes (ratio: {}%)", compressedBytes.length, String.format("%.1f", ratio));

            // 3. Subir a S3
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(backupBucket)
                    .key(s3Key)
                    .contentType("application/gzip")
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromBytes(compressedBytes));
            log.info("Uploaded to S3: bucket={}, key={}", backupBucket, s3Key);

            // 4. Construir URL de descarga
            String downloadUrl = publicBaseUrl + "/" + backupBucket + "/" + s3Key;

            // 5. Guardar metadata
            Backup backup = new Backup(
                    filename,
                    (long) uncompressedBytes.length,
                    (long) compressedBytes.length,
                    BackupStatus.COMPLETED,
                    s3Key
            );
            Backup saved = repository.save(backup);

            log.info("Backup completed successfully: id={}, filename={}", saved.getId(), filename);

            return toResponse(saved, downloadUrl);

        } catch (IOException e) {
            log.error("pg_dump not available or IO error: {}", e.getMessage());
            Backup failedBackup = new Backup(filename, 0L, 0L, BackupStatus.FAILED, s3Key);
            repository.save(failedBackup);
            throw new RuntimeException("pg_dump failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("pg_dump was interrupted", e);
        } catch (Exception e) {
            log.error("Backup failed: {}", e.getMessage());
            // Intentar guardar estado FAILED
            try {
                Backup failedBackup = new Backup(filename, 0L, 0L, BackupStatus.FAILED, s3Key);
                repository.save(failedBackup);
            } catch (Exception ex) {
                log.error("Failed to save failed backup status: {}", ex.getMessage());
            }
            throw new RuntimeException("Backup failed: " + e.getMessage(), e);
        }
    }

    /**
     * WHAT: Ejecuta pg_dump via ProcessBuilder y retorna el contenido sin comprimir.
     * WHY: Extraido como metodo protected para facilitar el testeo unitario
     *      (los tests pueden espiar este metodo sin ejecutar pg_dump real).
     * <p>
     * Lee variables de entorno: PGHOST, PGDIRECTPORT, PGUSER, PGPASSWORD, PGDATABASE.
     *
     * @return byte[] con el dump SQL sin comprimir
     * @throws IOException          Si pg_dump no esta disponible o hay error de I/O
     * @throws InterruptedException Si el proceso es interrumpido
     */
    protected byte[] executePgDump() throws IOException, InterruptedException {
        String pgHost = getEnvOrDefault("PGHOST", "localhost");
        String pgPort = getEnvOrDefault("PGDIRECTPORT", "5432");
        String pgUser = getEnvOrDefault("PGUSER", "postgres");
        String pgPassword = getEnvOrDefault("PGPASSWORD", "");
        String pgDatabase = getEnvOrDefault("PGDATABASE", "mundolimpio");

        ProcessBuilder pb = new ProcessBuilder(
                "pg_dump",
                "--no-owner",
                "--no-acl",
                "-h", pgHost,
                "-p", pgPort,
                "-U", pgUser,
                "-d", pgDatabase
        );
        pb.environment().put("PGPASSWORD", pgPassword);
        pb.environment().put("PGSSLMODE", "require");
        pb.redirectErrorStream(true);

        Process process = pb.start();

        // Leer stdout completo
        ByteArrayOutputStream outputBaos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int bytesRead;
        InputStream processStdout = process.getInputStream();
        while ((bytesRead = processStdout.read(buffer)) != -1) {
            outputBaos.write(buffer, 0, bytesRead);
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String errorOutput = outputBaos.toString();
            log.error("pg_dump failed with exit code {}: {}", exitCode, errorOutput);
            throw new IOException("pg_dump failed with exit code " + exitCode + ": " + errorOutput);
        }

        return outputBaos.toByteArray();
    }

    /**
     * WHAT: Lista todos los backups con paginación (BKP-002).
     * El ordenamiento se define via @PageableDefault en el controller.
     */
    @Transactional(readOnly = true)
    public Page<BackupResponse> getAllBackups(Pageable pageable) {
        return repository.findAll(pageable)
                .map(b -> toResponse(b, publicBaseUrl + "/" + backupBucket + "/" + b.getS3Key()));
    }

    /**
     * WHAT: Obtiene un backup por ID (BKP-003).
     *
     * @throws RuntimeException si no existe el backup
     */
    @Transactional(readOnly = true)
    public BackupResponse getBackupById(Long id) {
        Backup backup = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Backup not found with id: " + id));
        return toResponse(backup, publicBaseUrl + "/" + backupBucket + "/" + backup.getS3Key());
    }

    /**
     * WHAT: Descarga el archivo de backup desde S3.
     * WHY: Recupera el archivo comprimido desde Supabase Storage usando el s3Key
     *      guardado en la metadata del backup (BKP-003).
     *
     * @param id ID del backup
     * @return byte[] con el contenido comprimido (gzip)
     * @throws RuntimeException si el backup no existe o falla la descarga
     */
    public byte[] downloadBackup(Long id) {
        Backup backup = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Backup not found with id: " + id));

        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(backupBucket)
                    .key(backup.getS3Key())
                    .build();

            ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getRequest);
            return s3Object.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to download backup from S3: " + e.getMessage(), e);
        }
    }

    /**
     * WHAT: Convierte entidad Backup a DTO BackupResponse.
     * WHY: Separa la capa de persistencia de la API REST.
     *      No expone la entidad JPA directamente al cliente.
     */
    private BackupResponse toResponse(Backup backup, String downloadUrl) {
        return new BackupResponse(
                backup.getId(),
                backup.getFilename(),
                backup.getSize(),
                backup.getCompressedSize(),
                backup.getStatus(),
                backup.getCreatedAt(),
                downloadUrl
        );
    }

    /**
     * WHAT: Obtiene una variable de entorno con valor por defecto.
     * WHY: Las credenciales de BD se leen de env vars (no de application.yml)
     *      porque pg_dump es un proceso externo que no usa el pool de conexiones.
     */
    private String getEnvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null && !value.isEmpty() ? value : defaultValue;
    }
}
