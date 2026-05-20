package com.mundolimpio.application.user.domain;

/**
 * Enum que define los roles del sistema.
 * <p>
 * WHAT: 6 roles con permisos granulares para cada area funcional.
 * WHY: El modelo binario ADMIN/OPERATOR no escala. Cada rol ahora tiene
 * acceso solo a lo que necesita (principio de minimo privilegio).
 * DIFFERENCES: Se elimina OPERATOR (reemplazado por SALES_CLERK como
 * sucesor semantico) y se agregan 5 nuevos roles.
 */
public enum Role {
    /** WHAT: Control total del sistema (usuarios, configuracion, reportes) */
    ADMIN,
    /** WHAT: Gestion de stock: productos, inventario, compras, recibos */
    STOCK_MANAGER,
    /** WHAT: Operaciones de almacen: procesar recibos, ajustes basicos */
    STOCK_OPERATOR,
    /** WHAT: Ventas: crear ventas, consultar precios y productos */
    SALES_CLERK,
    /** WHAT: Produccion: crear y ejecutar lotes de produccion */
    PRODUCTION_OP,
    /** WHAT: Contabilidad: consultar ventas, costos y reportes financieros */
    ACCOUNTANT
}
