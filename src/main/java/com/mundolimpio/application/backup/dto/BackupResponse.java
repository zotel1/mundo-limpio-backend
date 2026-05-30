package com.mundolimpio.application.backup.dto;

import com.mundolimpio.application.backup.domain.BackupStatus;
import java.time.Instant;

/**
 * WHAT: DTO de respuesta para operaciones de backup.
 * WHY: Encapsula los datos que se envian al cliente al crear, listar o descargar backups.
 *      No expone la entidad Backup directamente (separa API de persistencia).
 *
 * @param id             Identificador unico del backup
 * @param filename       Nombre del archivo (ej: mundolimpio-20260530-143000.sql.gz)
 * @param size           Tamano original sin comprimir en bytes
 * @param compressedSize Tamano comprimido en bytes
 * @param status         Estado del backup (COMPLETED | FAILED)
 * @param createdAt      Fecha/hora de creacion
 * @param downloadUrl    URL publica o firmada para descargar el archivo
 */
public record BackupResponse(
    Long id,
    String filename,
    Long size,
    Long compressedSize,
    BackupStatus status,
    Instant createdAt,
    String downloadUrl
) {}
