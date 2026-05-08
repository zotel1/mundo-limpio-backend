package com.mundolimpio.application.sales.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidad que representa una venta completa.
 * 
 * POR QUÉ ESTA IMPLEMENTACIÓN:
 * - Usamos JPA con anotaciones porque Spring Boot lo soporta nativamente y evita
 *   boilerplate de JDBC manual.
 * - La relación @OneToMany con SaleItem nos permite tener una venta con múltiples
 *   items (cada item descuenta stock de un lote diferente vía FIFO).
 * - CascadeType.ALL + orphanRemoval = true significa que si borramos una venta,
 *   se borran automáticamente todos sus items. Esto mantiene la integridad.
 * - @Version implementa optimistic locking: si dos usuarios venden al mismo tiempo,
 *   el segundo recibe un error en vez de sobrescribir datos corruptos.
 * - El constructor protegido es requerido por JPA. El constructor público es el que
 *   usamos en nuestro código (patrón de inmutabilidad parcial).
 */
@Entity
@Table(name = "sales") // Nombre en plural porque la convención de la base de datos usa plurales
public class Sale {

    /**
     * ID autogenerado por la base de datos. Usamos IDENTITY porque es compatible
     * con PostgreSQL y MySQL sin configuración adicional.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Monto total de la venta. precision=10, scale=2 permite valores hasta 99,999,999.99
     * (8 dígitos enteros + 2 decimales). Suficiente para el negocio de Mundo Limpio.
     * Usamos BigDecimal en vez de Double porque dinero SIEMPRE requiere precisión exacta
     * (Double pierde precisión en operaciones aritméticas).
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    /**
     * Fecha/hora de creación. updatable=false asegura que JPA nunca modifique este valor
     * después de la inserción. La seteamos en el constructor con LocalDateTime.now().
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Relación uno-a-muchos con los items de la venta.
     * mappedBy = "sale" indica que SaleItem es el dueño de la relación (tiene la FK).
     * cascade = ALL permite persistir toda la venta + items en una sola operación.
     * orphanRemoval = true borra items huérfanos automáticamente.
     * Inicializamos con ArrayList vacío para evitar NullPointerException.
     */
    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SaleItem> items = new ArrayList<>();

    /**
     * Campo para optimistic locking. JPA incrementa este valor automáticamente en cada
     * actualización. Si dos transacciones leen el mismo dato y una escribe primero,
     * la segunda recibe OptimisticLockingFailureException.
     * POR QUÉ: Previene condiciones de carrera cuando dos ventas concurrentes intentan
     * descontar stock del mismo lote simultáneamente.
     */
    @Version
    private Long version;

    // Constructor protegido requerido por JPA (no se usa directamente en el código)
    protected Sale() {
    }

    /**
     * Constructor principal que usamos en el código.
     * @param totalAmount Monto total calculado previamente (suma de todos los items)
     */
    public Sale(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
        this.createdAt = LocalDateTime.now(); // Seteamos la fecha al momento de crear la venta
    }

    // Getters — no hay setters porque los campos se setean en el constructor o por JPA
    // Esto mantiene la entidad parcialmente inmutable (buena práctica de dominio)

    public Long getId() {
        return id;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<SaleItem> getItems() {
        return items;
    }

    /**
     * Agrega un item a la venta y establece la relación bidireccional.
     * POR QUÉ este método helper: Si solo hiciéramos items.add(item) sin
     * item.setSale(this), la relación quedaría rota del lado de SaleItem.
     * Este método asegura que ambos lados de la relación estén sincronizados.
     */
    public void addItem(SaleItem item) {
        items.add(item);
        item.setSale(this);
    }
}
