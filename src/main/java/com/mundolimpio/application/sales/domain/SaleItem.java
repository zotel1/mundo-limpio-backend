package com.mundolimpio.application.sales.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * Entidad que representa un item individual dentro de una venta.
 * 
 * POR QUÉ ESTA IMPLEMENTACIÓN:
 * - Una venta puede descontar stock de MÚLTIPLES lotes (ej: si pedís 10 unidades
 *   y el lote más viejo tiene 6, se toman 6 de ese lote y 4 del siguiente).
 *   Cada uno de estos "descontar parciales" es un SaleItem separado.
 * - @ManyToOne(fetch = LAZY) evita que Hibernate cargue toda la venta cuando solo
 *   necesitamos el item. Lazy loading = mejor performance.
 * - Guardamos productionBatchId (Long) en vez de una relación @ManyToOne con
 *   ProductionBatch porque el lote puede cambiar o eliminarse después, pero el
 *   historial de venta debe mantenerse intacto. Es un snapshot del momento de la venta.
 * - unitPriceAtSale y unitCostAtSale son snapshots del precio y costo en el momento
 *   exacto de la venta. Si el costo del lote cambia después, la venta no se altera.
 */
@Entity
@Table(name = "sale_items")
public class SaleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Relación con la venta padre. LAZY para no cargar toda la venta innecesariamente.
     * ElJoinColumn es la FK real en la base de datos.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    /**
     * ID del lote de producción del que se descontó stock.
     * POR QUÉ Long en vez de @ManyToOne: Necesitamos rastrear qué lote se usó
     * pero sin acoplar la entidad. Si el lote se borra, el item de venta sigue válido.
     */
    @Column(name = "production_batch_id", nullable = false)
    private Long productionBatchId;

    /** Cantidad de unidades descontadas de este lote en particular */
    @Column(nullable = false)
    private Integer quantity;

    /**
     * Precio unitario aplicado en esta venta. Snapshot del momento de la transacción.
     * NOTA: Actualmente usa getUnitCostAtProduction() del lote (costo = precio).
     * En el futuro se podría agregar un campo de precio de venta separado.
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPriceAtSale;

    /**
     * Costo unitario en el momento de la venta. Se usa para calcular márgenes de
     * ganancia después (venta - costo = ganancia).
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitCostAtSale;

    // Constructor protegido requerido por JPA
    protected SaleItem() {
    }

    /**
     * Constructor principal. Todos los campos son obligatorios porque un item
     * sin alguno de estos datos no tiene sentido de negocio.
     */
    public SaleItem(Long productionBatchId, Integer quantity, BigDecimal unitPriceAtSale, BigDecimal unitCostAtSale) {
        this.productionBatchId = productionBatchId;
        this.quantity = quantity;
        this.unitPriceAtSale = unitPriceAtSale;
        this.unitCostAtSale = unitCostAtSale;
    }

    // Getters y setter (solo setSale porque la relación bidireccional se setea
    // desde Sale.addItem(), los demás campos son inmutables después de crear)

    public Long getId() {
        return id;
    }

    public Sale getSale() {
        return sale;
    }

    /**
     * Setter para la relación bidireccional. Se llama desde Sale.addItem().
     */
    public void setSale(Sale sale) {
        this.sale = sale;
    }

    public Long getProductionBatchId() {
        return productionBatchId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPriceAtSale() {
        return unitPriceAtSale;
    }

    public BigDecimal getUnitCostAtSale() {
        return unitCostAtSale;
    }
}
