package com.mundolimpio.application.sales.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "sale_items")
public class SaleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @Column(name = "production_batch_id", nullable = false)
    private Long productionBatchId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPriceAtSale;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitCostAtSale;

    protected SaleItem() {
        // JPA only
    }

    public SaleItem(Long productionBatchId, Integer quantity, BigDecimal unitPriceAtSale, BigDecimal unitCostAtSale) {
        this.productionBatchId = productionBatchId;
        this.quantity = quantity;
        this.unitPriceAtSale = unitPriceAtSale;
        this.unitCostAtSale = unitCostAtSale;
    }

    public Long getId() {
        return id;
    }

    public Sale getSale() {
        return sale;
    }

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
