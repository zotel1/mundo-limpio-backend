package com.mundolimpio.application.receipt.domain;

import com.mundolimpio.application.user.domain.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidad que representa una compra registrada (ticket de proveedor).
 * 
 * POR QUÉ esta implementación:
 * - Referencia @ManyToOne a Supplier (proveedor) y User (admin que registró/confirmó).
 * - purchaseDate es LocalDate (solo fecha, sin hora) porque el ticket tiene fecha del día.
 * - status (PENDING/CONFIRMED) controla el flujo: OCR → revisión admin → confirmación.
 * - @OneToMany con cascade ALL + orphanRemoval: si se borra una compra, se borran sus items.
 * - @Version implementa optimistic locking para concurrencia.
 * - El constructor requiere todos los campos obligatorios (no tiene sentido una compra sin supplier o admin).
 */
@Entity
@Table(name = "purchases")
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * URL de la imagen del ticket almacenada en Supabase Storage.
     */
    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    /**
     * Proveedor de esta compra. FK a suppliers.
     * LAZY: no cargamos el proveedor a menos que sea necesario.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    /**
     * Admin (User) que registró o confirmó esta compra. FK a users.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", nullable = false)
    private User admin;

    /**
     * Fecha de la compra según el ticket (LocalDate, sin hora).
     */
    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    /**
     * Monto total de la compra: suma de todos los PurchaseItem.totalPrice.
     */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal total;

    /**
     * Estado de la compra: PENDING (recién procesada) o CONFIRMED (admin la revisó).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurchaseStatus status;

    /**
     * Fecha/hora de creación del registro en el sistema.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Relación uno-a-muchos con los items de la compra.
     * CascadeType.ALL + orphanRemoval: persistir/borrar items junto con la compra.
     */
    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseItem> items = new ArrayList<>();

    /**
     * Campo para optimistic locking. JPA incrementa automáticamente.
     */
    @Version
    private Long version;

    // Constructor protegido requerido por JPA
    protected Purchase() {
    }

    /**
     * Constructor principal.
     * @param imageUrl     URL de la imagen del ticket en Supabase Storage
     * @param supplier     Proveedor de la compra
     * @param admin        Admin que registra/confirma
     * @param purchaseDate Fecha de la compra (solo fecha, sin hora)
     * @param total        Monto total de la compra
     * @param status       Estado inicial (normalmente PENDING)
     */
    public Purchase(String imageUrl, Supplier supplier, User admin,
                    LocalDate purchaseDate, BigDecimal total, PurchaseStatus status) {
        this.imageUrl = imageUrl;
        this.supplier = supplier;
        this.admin = admin;
        this.purchaseDate = purchaseDate;
        this.total = total;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Agrega un item a la compra y establece la relación bidireccional.
     * POR QUÉ este método helper: Asegura que ambos lados de la relación
     * (Purchase → items y PurchaseItem → purchase) estén sincronizados.
     */
    public void addItem(PurchaseItem item) {
        items.add(item);
        item.setPurchase(this);
    }

    // Getters

    public Long getId() {
        return id;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public Supplier getSupplier() {
        return supplier;
    }

    public User getAdmin() {
        return admin;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public PurchaseStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<PurchaseItem> getItems() {
        return items;
    }

    public Long getVersion() {
        return version;
    }
}
