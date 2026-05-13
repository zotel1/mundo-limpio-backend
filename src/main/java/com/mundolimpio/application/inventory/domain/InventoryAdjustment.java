package com.mundolimpio.application.inventory.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entidad que representa un ajuste manual de inventario con auditoría completa.
 *
 * QUE HACE: Registra cada modificación manual al stock de un producto:
 *   - type: la razón semántica (ADJUSTMENT, BREAKAGE, RETURN, QUALITY_LOSS)
 *   - quantity: valor con signo (positivo = aumento, negativo = disminución)
 *   - reason: descripción textual del ajuste
 *   - created_at: timestamp automático de cuándo ocurrió
 *
 * POR QUE: REQ-2 exige un trail de auditoría completo y consultable para
 * todos los ajustes manuales. Una entidad separada permite:
 *   1. Consultar historial por inventory_id ordenado por fecha descendente
 *   2. Mantener integridad referencial via FK hacia inventory
 *   3. Crecimiento ilimitado sin afectar la tabla inventory (que es 1:1)
 *
 * DIFERENCIA con otras entidades del proyecto:
 *   - A diferencia de ProductionBatch (que usa Instant.now() para fecha),
 *     InventoryAdjustment usa prePersist con LocalDateTime.now() para
 *     asegurar que created_at se setee siempre, incluso si el cliente
 *     no lo envía.
 *   - La quantity usa convención de signo (no enum de dirección):
 *     positivo = incremento de stock, negativo = decremento.
 *     Esto evita un campo direction separado y permite sumar directamente.
 */
@Entity
@Table(name = "inventory_adjustments")
public class InventoryAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_id", nullable = false)
    private Inventory inventory;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;

    @Column(name = "reason")
    private String reason;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public InventoryAdjustment() {
    }

    /**
     * Crea un ajuste de inventario.
     *
     * @param inventory El inventario afectado
     * @param type      Tipo semántico (ADJUSTMENT, BREAKAGE, RETURN, QUALITY_LOSS)
     * @param quantity  Cantidad con signo (positivo = aumento, negativo = disminución)
     * @param reason    Descripción del ajuste
     */
    public InventoryAdjustment(Inventory inventory, String type, BigDecimal quantity, String reason) {
        this.inventory = inventory;
        this.type = type;
        this.quantity = quantity;
        this.reason = reason;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
