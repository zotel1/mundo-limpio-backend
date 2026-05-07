package com.mundolimpio.application.bulkproduct.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

/*Wl conversionRatio indica cuanto producto al final se obtiene
* por cada litro de materia prima:
* - Cloro: 1L puro -> 4L lavandina lista (ratio = 4)
* - Detergente: 1L base -> 3L detergente listo )ratio = 3)
* - Desodorante: 1L base -> 80L desodorante listo (ratio = 80)
* - Jabon: 1L -> 1L (ratio = 1, no se diluye)*/

@Entity
@Table(name = "bulkproducts")
public class BulkProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, name = "current_stock_liters")
    private BigDecimal currentStockLiters;

    @Column(nullable = false, name = "cost_per_liter")
    private BigDecimal costPerLiter;

    /*
    * Ratio de conversion: cuanto producto final obtienes por 1L de materia prima.
    * Ejemplo: 4 significa que 1L de cloro puro rinde 4L de lavandina.*/

    @Column(nullable = false, name = "conversion_ratio")
    private BigDecimal conversionRatio;

    @Version
    private Long version;

    public BulkProduct() {}

    public BulkProduct(Long id, String name,  BigDecimal currentStockLiters, BigDecimal costPerLiter, BigDecimal conversionRatio ) {
        this.id = id;
        this.name = name;
        this.currentStockLiters = currentStockLiters;
        this.costPerLiter = costPerLiter;
        this.conversionRatio = conversionRatio;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getCurrentStockLiters() {
        return currentStockLiters;
    }

    public void setCurrentStockLiters(BigDecimal currentStockLiters) {
        this.currentStockLiters = currentStockLiters;
    }

    public BigDecimal getCostPerLiter() {
        return costPerLiter;
    }

    public void setCostPerLiter(BigDecimal costPerLiter) {
        this.costPerLiter = costPerLiter;
    }

    public BigDecimal getConversionRatio() {
        return conversionRatio;
    }

    public void setConversionRatio(BigDecimal conversionRatio) {
        this.conversionRatio = conversionRatio;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
