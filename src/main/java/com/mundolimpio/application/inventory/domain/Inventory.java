package com.mundolimpio.application.inventory.domain;

import com.mundolimpio.application.product.domain.Product;
import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * Entidad que representa el stock actual de un producto específico.
 *
 * QUE HACE: Mantiene el saldo de stock (current_stock) para un producto,
 * más un umbral mínimo configurable (min_stock_threshold) para detectar
 * productos con stock bajo.
 *
 * POR QUE: Separamos Inventory de Product para mantener responsabilidades
 * distintas:
 *   - Product es una entidad de catálogo (SKU, nombre, precio activo).
 *     No debería tener campos operacionales como el stock.
 *   - Inventory es una entidad operacional con su propio ciclo de vida
 *     (ajustes manuales, alertas de low-stock, optimistic locking).
 *   - Al separarlas, un query de Product nunca arrastra datos de stock
 *     innecesariamente, y Inventory puede tener su propio @Version sin
 *     afectar la entidad Product.
 *
 * DIFERENCIA con la entidad Product:
 *   - Product NO tiene campo de stock (es catálogo puro).
 *   - Inventory SI tiene current_stock y usa @Version para optimistic
 *     locking, permitiendo concurrencia en ajustes de stock.
 *   - La relación es 1:1 vía FK+UNIQUE (product_id), no @OneToOne,
 *     porque la FK se crea desde Flyway sin depender de JPA.
 */
@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", unique = true, nullable = false)
    private Product product;

    @Column(name = "current_stock", nullable = false)
    private BigDecimal currentStock = BigDecimal.ZERO;

    @Column(name = "min_stock_threshold", nullable = false)
    private BigDecimal minStockThreshold = BigDecimal.ZERO;

    @Version
    private Long version;

    public Inventory() {
    }

    /**
     * Crea un Inventory para un producto específico.
     *
     * @param product  El producto asociado (no puede ser null)
     * @param currentStock Stock inicial (default 0 si no se especifica)
     */
    public Inventory(Product product, BigDecimal currentStock) {
        this.product = product;
        this.currentStock = currentStock != null ? currentStock : BigDecimal.ZERO;
        this.minStockThreshold = BigDecimal.ZERO;
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

    public BigDecimal getCurrentStock() {
        return currentStock;
    }

    public void setCurrentStock(BigDecimal currentStock) {
        this.currentStock = currentStock;
    }

    public BigDecimal getMinStockThreshold() {
        return minStockThreshold;
    }

    public void setMinStockThreshold(BigDecimal minStockThreshold) {
        this.minStockThreshold = minStockThreshold;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
