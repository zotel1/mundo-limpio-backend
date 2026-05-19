package com.mundolimpio.application.receipt.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Entidad que representa un proveedor.
 * 
 * POR QUÉ esta implementación:
 * - Tabla separada de purchases para normalización: un proveedor puede tener múltiples compras.
 * - createdAt se setea automáticamente en el constructor y es updatable=false.
 * - El constructor público es el que usamos en nuestro código; el protegido es para JPA.
 */
@Entity
@Table(name = "suppliers")
public class Supplier {

    /**
     * ID autogenerado. Usamos IDENTITY por compatibilidad con PostgreSQL.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Nombre del proveedor. Longitud 150 permite nombres compuestos o razones sociales largas.
     */
    @Column(nullable = false, length = 150)
    private String name;

    /**
     * Fecha/hora de creación. updatable=false: JPA nunca modifica este valor después de INSERT.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Constructor protegido requerido por JPA
    protected Supplier() {
    }

    /**
     * Constructor principal.
     * @param name Nombre del proveedor (ej: "Distribuidora XYZ S.A.")
     */
    public Supplier(String name) {
        this.name = name;
        this.createdAt = LocalDateTime.now();
    }

    // Getters — no setters porque los campos se setean en el constructor o por JPA

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
