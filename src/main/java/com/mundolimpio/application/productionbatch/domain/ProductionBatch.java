package com.mundolimpio.application.productionbatch.domain;

import com.mundolimpio.application.bulkproduct.domain.BulkProduct;
import com.mundolimpio.application.product.domain.Product;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entity representing a production batch.
 *
 * Links a finished product with the raw material used.
 * FIFO logic: system discounts from oldest batch first.
 */
@Entity
@Table(name = "production_batches")
public class ProductionBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bulk_product_id", nullable = false)
    private BulkProduct bulkProduct;

    @Column(name = "initial_quantity", nullable = false)
    private BigDecimal initialQuantity;

    @Column(name = "current_stock", nullable = false)
    private BigDecimal currentStock;

    @Column(name = "unit_cost_at_production", nullable = false)
    private BigDecimal unitCostAtProduction;

    @Column(name = "raw_quantity_used", nullable = false)
    private BigDecimal rawQuantityUsed;

    @Column(name = "production_date", nullable = false)
    private Instant productionDate;

    @Version
    private Long version;

    public ProductionBatch() {
    }

    public ProductionBatch(Product product, BulkProduct bulkProduct,
                          BigDecimal initialQuantity, BigDecimal currentStock,
                          BigDecimal unitCostAtProduction, BigDecimal rawQuantityUsed) {
        this.product = product;
        this.bulkProduct = bulkProduct;
        this.initialQuantity = initialQuantity;
        this.currentStock = currentStock;
        this.unitCostAtProduction = unitCostAtProduction;
        this.rawQuantityUsed = rawQuantityUsed;
        this.productionDate = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public BulkProduct getBulkProduct() {
        return bulkProduct;
    }

    public void setBulkProduct(BulkProduct bulkProduct) {
        this.bulkProduct = bulkProduct;
    }

    public BigDecimal getInitialQuantity() {
        return initialQuantity;
    }

    public void setInitialQuantity(BigDecimal initialQuantity) {
        this.initialQuantity = initialQuantity;
    }

    public BigDecimal getCurrentStock() {
        return currentStock;
    }

    public void setCurrentStock(BigDecimal currentStock) {
        this.currentStock = currentStock;
    }

    public BigDecimal getUnitCostAtProduction() {
        return unitCostAtProduction;
    }

    public void setUnitCostAtProduction(BigDecimal unitCostAtProduction) {
        this.unitCostAtProduction = unitCostAtProduction;
    }

    public BigDecimal getRawQuantityUsed() {
        return rawQuantityUsed;
    }

    public void setRawQuantityUsed(BigDecimal rawQuantityUsed) {
        this.rawQuantityUsed = rawQuantityUsed;
    }

    public Instant getProductionDate() {
        return productionDate;
    }

    public void setProductionDate(Instant productionDate) {
        this.productionDate = productionDate;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
