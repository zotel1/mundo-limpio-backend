package com.mundolimpio.application.receipt.mapper;

import com.mundolimpio.application.receipt.domain.Purchase;
import com.mundolimpio.application.receipt.domain.PurchaseItem;
import com.mundolimpio.application.receipt.domain.Supplier;
import com.mundolimpio.application.receipt.dto.*;
import com.mundolimpio.application.user.domain.User;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * WHAT: Mapper que convierte entre entidades de dominio y DTOs del módulo receipt.
 * WHY: Separar la API (DTOs) del dominio (entidades JPA) permite cambiar una capa
 *      sin afectar la otra. Si cambiamos la DB, la API no se rompe.
 * 
 * POR QUÉ implementación manual y no MapStruct:
 * - La lógica de conversión es simple (campos directos).
 * - No vale la pena la dependencia adicional por ahora.
 * - @Component permite inyección automática en los servicios.
 * 
 * DIFFERENCES con SaleMapper:
 * - toEntity recibe supplier y admin como parámetros (en vez de solo el request)
 *   porque el servicio ya busca/crea el Supplier y obtiene el User autenticado.
 * - Calcula totalPrice por item (quantity × unitPrice) automáticamente.
 */
@Component
public class ReceiptMapper {

    /**
     * Convierte un ReceiptConfirmRequest a una entidad Purchase.
     * 
     * POR QUÉ supplier y admin como parámetros separados:
     * - El servicio ya resolvió el Supplier (find-or-create) y el User (SecurityContext).
     * - El mapper no debería hacer lookups de DB — solo conversión de datos.
     * 
     * @param request  DTO con los datos revisados por el admin
     * @param supplier Entidad Supplier (ya persistida o nueva)
     * @param admin    Usuario admin autenticado
     * @return Purchase con items (sin persistir aún)
     */
    public Purchase toEntity(ReceiptConfirmRequest request, Supplier supplier, User admin) {
        Purchase purchase = new Purchase(
                request.imageUrl(),
                supplier,
                admin,
                request.purchaseDate(),
                BigDecimal.ZERO, // Se recalcula abajo
                com.mundolimpio.application.receipt.domain.PurchaseStatus.PENDING
        );

        // Convertir cada línea del request a PurchaseItem
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (ProductLineConfirmDto line : request.lines()) {
            BigDecimal lineTotal = line.unitPrice()
                    .multiply(BigDecimal.valueOf(line.quantity()));
            totalAmount = totalAmount.add(lineTotal);

            PurchaseItem item = new PurchaseItem(
                    line.description(),
                    line.quantity(),
                    line.unitPrice(),
                    lineTotal,
                    line.bulkProductId()
            );
            purchase.addItem(item);
        }

        // POR QUÉ recreamos Purchase:
        // - El total se calcula después de procesar todos los items (no se conoce antes).
        // - Purchase no tiene setter para total (inmutable parcial).
        // - Transferimos los items del Purchase temporal al final.
        Purchase finalPurchase = new Purchase(
                purchase.getImageUrl(),
                purchase.getSupplier(),
                purchase.getAdmin(),
                purchase.getPurchaseDate(),
                totalAmount,
                purchase.getStatus()
        );

        // Transferir items al nuevo Purchase (con el totalAmount correcto)
        for (PurchaseItem item : purchase.getItems()) {
            finalPurchase.addItem(item);
        }

        return finalPurchase;
    }

    /**
     * Convierte una entidad Purchase persistida a PurchaseResponse (DTO de salida).
     * 
     * @param purchase Purchase persistida con sus items
     * @return DTO listo para serializar a JSON
     */
    public PurchaseResponse toResponse(Purchase purchase) {
        List<PurchaseItemResponse> itemResponses = purchase.getItems().stream()
                .map(this::toItemResponse)
                .collect(Collectors.toList());

        return new PurchaseResponse(
                purchase.getId(),
                purchase.getImageUrl(),
                purchase.getSupplier().getName(),
                purchase.getPurchaseDate(),
                purchase.getTotal(),
                itemResponses
        );
    }

    /**
     * Convierte un PurchaseItem a PurchaseItemResponse.
     * private porque solo se usa internamente dentro del stream de toResponse().
     */
    private PurchaseItemResponse toItemResponse(PurchaseItem item) {
        return new PurchaseItemResponse(
                item.getId(),
                item.getDescription(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getTotalPrice(),
                item.getBulkProductId()
        );
    }
}
