package com.mundolimpio.application.backup.domain;

/**
 * WHAT: Enum que representa el estado de un backup de base de datos.
 * WHY: Permite saber si el backup se completo exitosamente o fallo,
 *      tanto en la entidad como en la respuesta al cliente.
 */
public enum BackupStatus {
    /**
     * Backup completado exitosamente: pg_dump + gzip + S3 upload OK.
     */
    COMPLETED,

    /**
     * Backup fallido: pg_dump error, S3 no disponible, o cualquier
     * excepcion durante el proceso.
     */
    FAILED
}
