package com.mundolimpio.application.audit.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Entidad JPA para el registro de auditoria (audit_log).
 * <p>
 * WHAT: Registra acciones sensibles del sistema: cambios de rol, ajustes de stock,
 * eliminacion de productos, confirmacion de recepciones, ejecucion de produccion.
 * Cada entrada captura: quien (actor_id), que hizo (action), sobre que entidad
 * (entity_type + entity_id), y los valores antes/despues (old_value/new_value).
 * <p>
 * WHY: UR-R7 requiere audit trail para operaciones sensibles. Esta entidad
 * persiste en la tabla `audit_log` creada por Flyway V9. Los campos old_value
 * y new_value son TEXT (sin limite) para soportar representaciones JSON de
 * objetos complejos si es necesario.
 * <p>
 * DIFFERENCES: Antes no existia auditoria en el sistema. Esta es la primera
 * entidad del modulo audit. Sigue el mismo patron que User (IDENTITY, Instant,
 * constructor sin args requerido por JPA).
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", length = 50)
    private String entityId;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Constructor sin args requerido por JPA/Hibernate.
     */
    public AuditLog() {
    }

    /**
     * Constructor completo para creacion programatica.
     * <p>
     * WHAT: Inicializa todos los campos. createdAt se asigna automaticamente
     * al momento actual si no se provee.
     * WHY: Usado por AuditLogService.logAsync() para construir la entidad
     * antes de persistir.
     *
     * @param actorId    ID del usuario que realizo la accion
     * @param action     tipo de accion (ROLES_CHANGED, STOCK_ADJUST, etc.)
     * @param entityType tipo de entidad afectada (USER, PRODUCT, etc.)
     * @param entityId   ID opcional de la entidad afectada
     * @param oldValue   valor anterior (opcional, ej: roles viejos)
     * @param newValue   valor nuevo (opcional, ej: roles nuevos)
     */
    public AuditLog(Long actorId, String action, String entityType,
                    String entityId, String oldValue, String newValue) {
        this.actorId = actorId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.createdAt = Instant.now();
    }

    // Getters necesarios para JPA y tests

    public Long getId() {
        return id;
    }

    public Long getActorId() {
        return actorId;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
