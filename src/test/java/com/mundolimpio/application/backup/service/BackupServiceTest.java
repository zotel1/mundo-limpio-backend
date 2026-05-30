package com.mundolimpio.application.backup.service;

import com.mundolimpio.application.backup.domain.Backup;
import com.mundolimpio.application.backup.domain.BackupStatus;
import com.mundolimpio.application.backup.dto.BackupResponse;
import com.mundolimpio.application.backup.repository.BackupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.isA;

/**
 * Unit Test for BackupService.
 * Uses Mockito to mock all external dependencies: BackupRepository, S3Client.
 * The protected executePgDump() method is stubbed to avoid actual pg_dump execution.
 */
@ExtendWith(MockitoExtension.class)
class BackupServiceTest {

    @Mock
    private BackupRepository repository;

    @Mock
    private S3Client s3Client;

    private BackupService service;

    @BeforeEach
    void setUp() {
        service = spy(new BackupService(
                repository,
                s3Client,
                "backups",              // app.backup.bucket
                "https://public.url",   // app.supabase.public-base-url
                "https://endpoint"      // app.supabase.endpoint
        ));
    }

    // ==================== CREATE BACKUP TESTS ====================

    /**
     * Test: createBackup debe ejecutar pg_dump exitosamente, comprimir, subir a S3 y persistir.
     * <p>
     * QUE VERIFICA:
     * - executePgDump() es llamado internamente
     * - Los datos se comprimen con gzip y se suben a S3 via putObject()
     * - El backup se persiste con estado COMPLETED
     * - La respuesta contiene todos los campos esperados
     */
    @Test
    void shouldCreateBackupSuccessfully() throws Exception {
        // Given
        byte[] mockSqlContent = "CREATE TABLE test (id INT);\nINSERT INTO test VALUES (1);".getBytes();
        doReturn(mockSqlContent).when(service).executePgDump();

        Backup savedBackup = new Backup(
                "mundolimpio-20260530-143000.sql.gz",
                (long) mockSqlContent.length,
                50L,
                BackupStatus.COMPLETED,
                "backups/mundolimpio-20260530-143000.sql.gz"
        );
        setId(savedBackup, 1L);

        when(repository.save(any(Backup.class))).thenReturn(savedBackup);

        // When
        BackupResponse response = service.createBackup();

        // Then
        assertNotNull(response);
        assertEquals(1L, response.id());
        assertEquals(BackupStatus.COMPLETED, response.status());
        assertEquals(mockSqlContent.length, response.size());
        assertEquals(50L, response.compressedSize());
        assertTrue(response.filename().contains("mundolimpio-"));
        assertTrue(response.downloadUrl().contains("backups/"));
        assertNotNull(response.createdAt());

        verify(service).executePgDump();
        verify(s3Client).putObject(isA(PutObjectRequest.class), isA(RequestBody.class));
        verify(repository).save(any(Backup.class));
    }

    /**
     * Test: createBackup debe persistir FAILED cuando pg_dump falla.
     * <p>
     * QUE VERIFICA:
     * - executePgDump() lanza IOException
     * - El servicio captura, persiste FAILED y relanza RuntimeException
     * - NO se llama a S3
     */
    @Test
    void shouldHandlePgDumpFailure() throws Exception {
        // Given
        doThrow(new IOException("pg_dump not found")).when(service).executePgDump();

        // When & Then
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.createBackup());
        assertTrue(ex.getMessage().contains("pg_dump failed"));

        verify(service).executePgDump();
        verify(repository).save(argThat(b -> b.getStatus() == BackupStatus.FAILED));
        verify(s3Client, never()).putObject(isA(PutObjectRequest.class), isA(RequestBody.class));
    }

    /**
     * Test: createBackup debe persistir FAILED cuando S3 falla.
     * <p>
     * QUE VERIFICA:
     * - executePgDump() retorna datos exitosamente
     * - S3Client.putObject() lanza RuntimeException
     * - El servicio captura, persiste FAILED y relanza RuntimeException
     */
    @Test
    void shouldHandleS3Exception() throws Exception {
        // Given
        byte[] mockSqlContent = "some sql data".getBytes();
        doReturn(mockSqlContent).when(service).executePgDump();

        when(s3Client.putObject(isA(PutObjectRequest.class), isA(RequestBody.class)))
                .thenThrow(new RuntimeException("S3 upload failed: Bucket not found"));

        // When & Then
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.createBackup());
        assertTrue(ex.getMessage().contains("Backup failed"));

        verify(service).executePgDump();
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(repository).save(argThat(b -> b.getStatus() == BackupStatus.FAILED));
    }

    // ==================== GET ALL BACKUPS TESTS ====================

    /**
     * Test: getAllBackups debe retornar todos los backups ordenados DESC.
     * <p>
     * QUE VERIFICA:
     * - repository.findAllByOrderByCreatedAtDesc() es llamado
     * - Los 2 backups se retornan en el orden correcto (nuevo primero)
     * - Cada BackupResponse contiene los campos correctos
     */
    @Test
    void shouldGetAllBackups() {
        // Given
        Backup older = createBackupWithId(1L, "backup-old.sql.gz", BackupStatus.COMPLETED,
                Instant.parse("2026-05-01T10:00:00Z"));
        Backup newer = createBackupWithId(2L, "backup-new.sql.gz", BackupStatus.COMPLETED,
                Instant.parse("2026-05-28T10:00:00Z"));

        Pageable pageable = PageRequest.of(0, 20);
        Page<Backup> backupPage = new PageImpl<>(List.of(newer, older), pageable, 2);
        when(repository.findAll(any(Pageable.class))).thenReturn(backupPage);

        // When
        Page<BackupResponse> responses = service.getAllBackups(pageable);

        // Then
        assertNotNull(responses);
        assertEquals(2, responses.getContent().size());
        assertEquals(2L, responses.getContent().get(0).id());
        assertEquals("backup-new.sql.gz", responses.getContent().get(0).filename());
        assertEquals(1L, responses.getContent().get(1).id());

        verify(repository).findAll(any(Pageable.class));
    }

    /**
     * Test: getAllBackups debe retornar lista vacia cuando no hay backups.
     * <p>
     * QUE VERIFICA:
     * - repository devuelve lista vacia
     * - El resultado es una lista vacia (no null)
     */
    @Test
    void shouldGetAllBackupsWhenEmpty() {
        // Given
        Pageable pageable = PageRequest.of(0, 20);
        Page<Backup> emptyPage = new PageImpl<>(List.of(), pageable, 0);
        when(repository.findAll(any(Pageable.class))).thenReturn(emptyPage);

        // When
        Page<BackupResponse> responses = service.getAllBackups(pageable);

        // Then
        assertNotNull(responses);
        assertTrue(responses.getContent().isEmpty());

        verify(repository).findAll(any(Pageable.class));
    }

    // ==================== GET BACKUP BY ID TESTS ====================

    /**
     * Test: getBackupById debe retornar el backup cuando existe.
     */
    @Test
    void shouldGetBackupById() {
        // Given
        Backup backup = createBackupWithId(1L, "test-backup.sql.gz", BackupStatus.COMPLETED,
                Instant.parse("2026-05-15T10:00:00Z"));
        when(repository.findById(1L)).thenReturn(Optional.of(backup));

        // When
        BackupResponse response = service.getBackupById(1L);

        // Then
        assertNotNull(response);
        assertEquals(1L, response.id());
        assertEquals("test-backup.sql.gz", response.filename());
        assertEquals(BackupStatus.COMPLETED, response.status());

        verify(repository).findById(1L);
    }

    /**
     * Test: getBackupById debe lanzar RuntimeException cuando no existe.
     */
    @Test
    void shouldThrowWhenBackupNotFound() {
        // Given
        when(repository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.getBackupById(999L));
        assertTrue(ex.getMessage().contains("Backup not found"));
        assertTrue(ex.getMessage().contains("999"));

        verify(repository).findById(999L);
    }

    // ==================== DOWNLOAD BACKUP TESTS ====================

    /**
     * Test: downloadBackup debe obtener el archivo de S3 y retornar los bytes.
     * <p>
     * QUE VERIFICA:
     * - repository.findById() devuelve el backup
     * - S3Client.getObject() es llamado con el s3Key correcto
     * - Los bytes retornados coinciden con los del mock
     */
    @Test
    void shouldDownloadBackup() throws Exception {
        // Given
        Long backupId = 1L;
        byte[] mockFileData = "compressed gzip binary data".getBytes();

        Backup backup = createBackupWithId(backupId, "download-test.sql.gz", BackupStatus.COMPLETED,
                Instant.parse("2026-05-20T10:00:00Z"));

        when(repository.findById(backupId)).thenReturn(Optional.of(backup));

        GetObjectResponse getObjectResponse = GetObjectResponse.builder().build();
        ResponseInputStream<GetObjectResponse> responseStream =
                new ResponseInputStream<>(getObjectResponse, new ByteArrayInputStream(mockFileData));
        when(s3Client.getObject(isA(GetObjectRequest.class))).thenReturn(responseStream);

        // When
        byte[] result = service.downloadBackup(backupId);

        // Then
        assertNotNull(result);
        assertArrayEquals(mockFileData, result);

        verify(repository).findById(backupId);
        verify(s3Client).getObject(isA(GetObjectRequest.class));
    }

    /**
     * Test: downloadBackup debe lanzar RuntimeException cuando el backup no existe.
     */
    @Test
    void shouldThrowWhenDownloadBackupNotFound() {
        // Given
        when(repository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.downloadBackup(999L));
        assertTrue(ex.getMessage().contains("Backup not found"));

        verify(repository).findById(999L);
        verify(s3Client, never()).getObject(isA(GetObjectRequest.class));
    }

    // ==================== HELPERS ====================

    /**
     * Helper: crea un Backup completo via reflection para el test.
     * WHY: Backup entity no expone setId() ni un constructor que acepte id y createdAt.
     */
    private Backup createBackupWithId(Long id, String filename, BackupStatus status, Instant createdAt) {
        Backup backup = new Backup(filename, 100L, 50L, status, "backups/" + filename);
        setId(backup, id);
        setCreatedAt(backup, createdAt);
        return backup;
    }

    private void setId(Backup backup, Long id) {
        try {
            var field = Backup.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(backup, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set id", e);
        }
    }

    private void setCreatedAt(Backup backup, Instant createdAt) {
        try {
            var field = Backup.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(backup, createdAt);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set createdAt", e);
        }
    }
}
