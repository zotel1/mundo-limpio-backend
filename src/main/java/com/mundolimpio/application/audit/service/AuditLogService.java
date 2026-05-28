package com.mundolimpio.application.audit.service;

import com.mundolimpio.application.audit.domain.AuditLog;
import com.mundolimpio.application.audit.repository.AuditLogRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de auditoria asincrono.
 * <p>
 * WHAT: Provee logAsync() que construye y persiste una entrada de auditoria
 * de forma asincrona (@Async) en una transaccion independiente (REQUIRES_NEW).
 * Esto garantiza que la escritura de auditoria no se revierte si la transaccion
 * del caller falla, y no bloquea el hilo principal.
 * <p>
 * WHY: UR-R7 requiere que los eventos de auditoria se registren incluso si
 * la operacion principal es rechazada por reglas de negocio (ej: intento de
 * autodemocion). @Async + REQUIRES_NEW aseguran:
 * - No bloqueante: el caller no espera a que se escriba el log
 * - Sobrevive rollback: transaccion independiente del caller
 * <p>
 * DIFFERENCES: Es el primer servicio @Async del sistema. Los demas servicios
 * (AuthService, UserManagementService) son sincronicos y transaccionales
 * con propagacion por defecto (REQUIRED).
 */
@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Registra una entrada de auditoria de forma asincrona.
     * <p>
     * WHAT: Construye un AuditLog con los parametros recibidos y lo persiste
     * via AuditLogRepository.save(). La anotacion @Async delega la ejecucion
     * al TaskExecutor de Spring; REQUIRES_NEW abre una transaccion independiente
     * que commitea sin importar el resultado de la transaccion del caller.
     * <p>
     * WHY: Si el caller hace rollback (ej: validacion de negocio rechaza la
     * operacion), la entrada de auditoria YA fue commiteada en su propia
     * transaccion. Esto cumple UR-R7: "incluso intentos fallidos deben
     * registrarse".
     *
     * @param actorId    ID del usuario que realizo la accion
     * @param action     tipo de accion (ROLES_CHANGED, STOCK_ADJUST, etc.)
     * @param entityType tipo de entidad afectada (USER, PRODUCT, etc.)
     * @param entityId   ID opcional de la entidad afectada
     * @param oldValue   valor anterior (opcional)
     * @param newValue   valor nuevo (opcional)
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAsync(Long actorId, String action, String entityType,
                         String entityId, String oldValue, String newValue) {
        AuditLog entry = new AuditLog(actorId, action, entityType,
                entityId, oldValue, newValue);
        auditLogRepository.save(entry);
    }
}
