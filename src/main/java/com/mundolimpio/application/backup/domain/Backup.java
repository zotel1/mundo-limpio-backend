package com.mundolimpio.application.backup.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * WHAT: Entidad que representa un backup de la base de datos.
 * WHY: Guarda metadata de cada backup ejecutado para poder listarlos y descargarlos.
 *
 * @param id Identificador unico auto-generado
 * @param filename Nombre del archivo (ej: mundolimpio-20260530-143000.sql.gz)
 * @param size Tamano original sin comprimir en bytes
 * @param compressedSize Tamano comprimido en bytes
 * @param status COMPLETED o FAILED
 * @param s3Key Key en Supabase Storage (ej: backups/mundolimpio-20260530-143000.sql.gz)
 * @param createdAt Fecha/hora de creacion
 */
@Entity
@Table(name = "backups")
public class Backup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false)
    private Long size;

    @Column(nullable = false, name = "compressed_size")
    private Long compressedSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BackupStatus status;

    @Column(nullable = false, name = "s3_key", length = 500)
    private String s3Key;

    @Column(nullable = false, name = "created_at")
    private Instant createdAt;

    // Required by JPA
    protected Backup() {}

    public Backup(String filename, Long size, Long compressedSize, BackupStatus status, String s3Key) {
        this.filename = filename;
        this.size = size;
        this.compressedSize = compressedSize;
        this.status = status;
        this.s3Key = s3Key;
        this.createdAt = Instant.now();
    }

    // Getters
    public Long getId() { return id; }
    public String getFilename() { return filename; }
    public Long getSize() { return size; }
    public Long getCompressedSize() { return compressedSize; }
    public BackupStatus getStatus() { return status; }
    public String getS3Key() { return s3Key; }
    public Instant getCreatedAt() { return createdAt; }
}
