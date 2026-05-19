package com.mundolimpio.application.receipt.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * Entidad que representa un item individual dentro de una compra.
 * 
 * POR QUÉ esta implementación:
 * - Cada línea del ticket de compra se convierte en un PurchaseItem.
 * - bulkProductId es nullable: si el OCR no matchea un producto con el catálogo,
 *   el admin puede dejar el item sin vincular (se persiste igual).
 * - totalPrice = quantity × unitPrice (calculado en el servicio, no en la entidad).
 * - @ManyToOne(fetch = LAZY) con Purchase evita cargar toda la compra
 *   cuando solo necesitamos los items.
 */
@Entity
@Table(name = "purchase_items")
public class PurchaseItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Relación con la compra padre. LAZY para no cargar la compra innecesariamente.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_id", nullable = false)
    private Purchase purchase;

    /**
     * Descripción del producto comprado (extraído del ticket por OCR).
     */
    @Column(nullable = false, length = 255)
    private String description;

    /**
     * Cantidad comprada de este producto.
     */
    @Column(nullable = false)
    private Integer quantity;

    /**
     * Precio unitario en el momento de la compra.
     */
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    /**
     * Precio total de esta línea: quantity × unitPrice.
     */
    @Column(name = "total_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalPrice;

    /**
     * ID del BulkProduct matcheado (nullable = producto no reconocido por el OCR).
     * FK a bulk_products con ON DELETE SET NULL: si el BulkProduct se borra,
     * este campo se pone a null pero el PurchaseItem se conserva (historial).
     */
    @Column(name = "bulk_product_id")
    private Long bulkProductId;

    // Constructor protegido requerido por JPA
    protected PurchaseItem() {
    }

    /**
     * Constructor principal.
     * @param description   Descripción del producto (ej: "Cloro 5L")
     * @param quantity      Cantidad comprada
     * @param unitPrice     Precio unitario
     * @param totalPrice    Precio total (qty × unitPrice)
     * @param bulkProductId ID del BulkProduct matcheado, o null si no se reconoce
     */
    public PurchaseItem(String description, Integer quantity,
                        BigDecimal unitPrice, BigDecimal totalPrice, Long bulkProductId) {
        this.description = description;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalPrice = totalPrice;
        this.bulkProductId = bulkProductId;
    }

    // Getters y setter (solo setPurchase para la relación bidireccional)

    public Long getId() {
        return id;
    }

    public Purchase getPurchase() {
        return purchase;
    }

    /**
     * Setter para la relación bidireccional. Llamado desde Purchase.addItem().
     */
    public void setPurchase(Purchase purchase) {
        this.purchase = purchase;
    }

    public String getDescription() {
        return description;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public Long getBulkProductId() {
        return bulkProductId;
    }
}
