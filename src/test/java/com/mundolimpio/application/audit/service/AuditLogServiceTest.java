package com.mundolimpio.application.audit.service;

import com.mundolimpio.application.audit.domain.AuditLog;
import com.mundolimpio.application.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test para AuditLogService.logAsync().
 * <p>
 * WHAT: Verifica que logAsync() construye correctamente la entidad AuditLog
 * y la persiste via AuditLogRepository.save(). Tambien verifica que los campos
 * opcionales (oldValue, newValue, entityId) pueden ser null sin romper nada.
 * <p>
 * WHY: AuditLogService usa @Async + REQUIRES_NEW para escritura no bloqueante
 * que sobrevive rollback del caller. Testeamos la logica de construccion de
 * la entidad y la delegacion al repositorio de forma aislada con Mockito.
 */
@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditLogService auditLogService;

    /**
     * Test 1: logAsync con todos los campos poblados guarda correctamente.
     * <p>
     * WHAT: Verifica que el metodo construye un AuditLog con los valores exactos
     * recibidos por parametro y llama a repository.save() una sola vez.
     * <p>
     * WHY: Cada campo del AuditLog debe reflejar exactamente lo que el caller
     * paso. Si se truncan o intercambian valores, el rastro de auditoria
     * pierde integridad.
     */
    @Test
    void logAsync_withAllFields_shouldSaveCorrectAuditEntry() {
        // Given: datos completos para una entrada de auditoria
        Long actorId = 1L;
        String action = "ROLES_CHANGED";
        String entityType = "USER";
        String entityId = "5";
        String oldValue = "[SALES_CLERK]";
        String newValue = "[STOCK_MANAGER, SALES_CLERK]";

        // When: ejecutamos logAsync
        auditLogService.logAsync(actorId, action, entityType, entityId, oldValue, newValue);

        // Then: se debe haber llamado a repository.save() con la entidad correcta
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertNotNull(saved, "La entidad guardada no debe ser null");
        assertEquals(actorId, saved.getActorId(), "actorId debe coincidir");
        assertEquals(action, saved.getAction(), "action debe coincidir");
        assertEquals(entityType, saved.getEntityType(), "entityType debe coincidir");
        assertEquals(entityId, saved.getEntityId(), "entityId debe coincidir");
        assertEquals(oldValue, saved.getOldValue(), "oldValue debe coincidir");
        assertEquals(newValue, saved.getNewValue(), "newValue debe coincidir");
        assertNotNull(saved.getCreatedAt(), "createdAt debe ser asignado automaticamente");
    }

    /**
     * Test 2: logAsync con campos opcionales null guarda sin errores.
     * <p>
     * WHAT: Verifica que los campos opcionales (entityId, oldValue, newValue)
     * pueden ser null y el servicio igual persiste la entrada de auditoria.
     * <p>
     * WHY: No todas las acciones de auditoria tienen valor anterior/nuevo
     * (ej: DELETE no tiene newValue, LOGIN no tiene entityId especifico).
     * El servicio debe aceptar nulls sin lanzar NullPointerException.
     */
    @Test
    void logAsync_withNullOptionalFields_shouldStillSave() {
        // Given: solo campos obligatorios, opcionales en null
        Long actorId = 2L;
        String action = "LOGIN";
        String entityType = "AUTH";

        // When: ejecutamos logAsync con opcionales null
        auditLogService.logAsync(actorId, action, entityType, null, null, null);

        // Then: repository.save() es llamado, los opcionales son null
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals(actorId, saved.getActorId());
        assertEquals(action, saved.getAction());
        assertEquals(entityType, saved.getEntityType());
        assertNull(saved.getEntityId(), "entityId opcional debe ser null si no se pasa");
        assertNull(saved.getOldValue(), "oldValue opcional debe ser null si no se pasa");
        assertNull(saved.getNewValue(), "newValue opcional debe ser null si no se pasa");
        assertNotNull(saved.getCreatedAt(), "createdAt siempre debe ser asignado");
    }

    /**
     * Test 3: logAsync con entityId vacio (no null) guarda correctamente.
     * <p>
     * WHAT: Verifica que un entityId de tipo string vacio (no null) se
     * persiste tal cual. Distinto de null — "" es un valor valido.
     * <p>
     * WHY: Edge case: algun caller podria pasar "" en vez de null.
     * El repositorio debe aceptarlo; la validacion de negocio (si "" es
     * invalido) va en el caller, no en AuditLogService.
     */
    @Test
    void logAsync_withEmptyEntityId_shouldSaveEmptyString() {
        // Given: entityId = "" (no null)
        auditLogService.logAsync(3L, "EXPORT", "REPORT", "", null, null);

        // Then
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        assertEquals("", captor.getValue().getEntityId(),
                "entityId vacio debe persistirse como string vacio, no null");
    }
}
