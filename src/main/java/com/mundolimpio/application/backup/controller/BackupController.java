package com.mundolimpio.application.backup.controller;

import com.mundolimpio.application.backup.dto.BackupResponse;
import com.mundolimpio.application.backup.service.BackupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * WHAT: Controlador REST para gestion de backups de base de datos.
 * WHY: Expone los endpoints POST (crear backup), GET (listar) y GET /{id}/download
 *      (descargar archivo) exclusivamente para usuarios ADMIN.
 *      Sigue el mismo patron que ProductionBatchController y ReceiptController.
 */
@RestController
@RequestMapping("/api/v1/admin/backups")
@Tag(name = "Backups", description = "Endpoints para gestion de backups de BD (solo ADMIN)")
public class BackupController {

    private final BackupService backupService;

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    /**
     * WHAT: Ejecuta un backup manual de la base de datos.
     * WHY: El ADMIN puede triggerear un backup via HTTP POST.
     *      El servicio ejecuta pg_dump, comprime, sube a S3 y persiste metadata.
     *
     * @return 201 Created con BackupResponse si el backup se completa exitosamente
     *         400 Bad Request si pg_dump falla o no esta disponible
     *         403 Forbidden si el usuario no es ADMIN
     *         503 Service Unavailable si S3 no esta disponible
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a database backup",
            description = "Executes pg_dump, compresses with gzip, uploads to Supabase Storage. ADMIN only.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Backup created successfully"),
            @ApiResponse(responseCode = "400", description = "pg_dump failed or not available"),
            @ApiResponse(responseCode = "403", description = "Forbidden: ADMIN only"),
            @ApiResponse(responseCode = "503", description = "S3/Storage unavailable")
    })
    public ResponseEntity<BackupResponse> createBackup() {
        BackupResponse response = backupService.createBackup();
        return ResponseEntity.status(201).body(response);
    }

    /**
     * WHAT: Lista todos los backups con paginación.
     *
     * @param pageable Paginación y ordenamiento (default: sort by createdAt DESC)
     * @return 200 OK con página de BackupResponse (puede ser vacía)
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all backups",
            description = "Returns a paginated list of backups ordered by creation date descending. ADMIN only.")
    @ApiResponse(responseCode = "200", description = "Paginated list of backups")
    public ResponseEntity<Page<BackupResponse>> getAllBackups(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(backupService.getAllBackups(pageable));
    }

    /**
     * WHAT: Descarga un archivo de backup desde Supabase Storage.
     * WHY: Recupera el archivo comprimido desde S3 y lo envia como attachment.
     *
     * @param id ID del backup a descargar
     * @return 200 OK con el archivo gzip como attachment
     *         404 Not Found si el backup no existe
     *         403 Forbidden si el usuario no es ADMIN
     */
    @GetMapping("/{id}/download")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Download a backup file",
            description = "Downloads a backup file from Supabase Storage. ADMIN only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Backup file"),
            @ApiResponse(responseCode = "404", description = "Backup not found"),
            @ApiResponse(responseCode = "403", description = "Forbidden: ADMIN only")
    })
    public ResponseEntity<byte[]> downloadBackup(@PathVariable Long id) {
        BackupResponse metadata = backupService.getBackupById(id);
        byte[] data = backupService.downloadBackup(id);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + metadata.filename() + "\"")
                .body(data);
    }
}
