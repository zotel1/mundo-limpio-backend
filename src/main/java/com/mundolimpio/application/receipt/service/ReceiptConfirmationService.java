package com.mundolimpio.application.receipt.service;

import com.mundolimpio.application.bulkproduct.domain.BulkProduct;
import com.mundolimpio.application.bulkproduct.repository.BulkProductRepository;
import com.mundolimpio.application.receipt.domain.Purchase;
import com.mundolimpio.application.receipt.domain.PurchaseItem;
import com.mundolimpio.application.receipt.domain.Supplier;
import com.mundolimpio.application.receipt.dto.PurchaseResponse;
import com.mundolimpio.application.receipt.dto.ReceiptConfirmRequest;
import com.mundolimpio.application.receipt.mapper.ReceiptMapper;
import com.mundolimpio.application.receipt.repository.PurchaseRepository;
import com.mundolimpio.application.receipt.repository.SupplierRepository;
import com.mundolimpio.application.user.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * WHAT: Servicio que orquesta el flujo de confirmación de una compra.
 * WHY: El admin revisa los datos del OCR, corrige si es necesario, y confirma.
 *      Este servicio persiste la Purchase + PurchaseItems, crea el Supplier si no existe,
 *      y actualiza el stock de BulkProduct para los items matcheados.
 * 
 * FLUJO:
 * 1. Buscar o crear Supplier por nombre
 * 2. Mapear ReceiptConfirmRequest → Purchase (con status CONFIRMED)
 * 3. Guardar Purchase (cascade persiste los PurchaseItems)
 * 4. Para cada item con bulkProductId: incrementar stock de BulkProduct
 * 5. Retornar PurchaseResponse
 * 
 * DIFFERENCES con ReceiptProcessingService:
 * - Este servicio SÍ persiste en la base de datos (Purchase, PurchaseItems).
 * - Este servicio SÍ interactúa con repositorios JPA y BulkProductRepository.
 * - Es @Transactional para atomicidad: si falla el stock update, el purchase no se persiste.
 */
@Service
@Transactional
public class ReceiptConfirmationService {

    private static final Logger log = LoggerFactory.getLogger(ReceiptConfirmationService.class);

    private final SupplierRepository supplierRepository;
    private final PurchaseRepository purchaseRepository;
    private final BulkProductRepository bulkProductRepository;
    private final ReceiptMapper mapper;

    /**
     * Inyección por constructor de todos los repositorios y el mapper.
     */
    public ReceiptConfirmationService(SupplierRepository supplierRepository,
                                       PurchaseRepository purchaseRepository,
                                       BulkProductRepository bulkProductRepository,
                                       ReceiptMapper mapper) {
        this.supplierRepository = supplierRepository;
        this.purchaseRepository = purchaseRepository;
        this.bulkProductRepository = bulkProductRepository;
        this.mapper = mapper;
    }

    /**
     * Confirma una compra: persiste Purchase + Items, crea Supplier si no existe,
     * y actualiza el stock de BulkProduct para los items matcheados.
     *
     * @param request DTO con los datos revisados por el admin
     * @param admin   Usuario admin autenticado que confirma la compra
     * @return PurchaseResponse con los datos persistidos
     */
    public PurchaseResponse confirm(ReceiptConfirmRequest request, User admin) {
        // Paso 1: Buscar o crear Supplier por nombre
        Supplier supplier = supplierRepository.findByName(request.supplierName())
                .orElseGet(() -> {
                    Supplier newSupplier = new Supplier(request.supplierName());
                    return supplierRepository.save(newSupplier);
                });

        // Paso 2: Mapear ReceiptConfirmRequest → Purchase (con status CONFIRMED)
        Purchase purchase = mapper.toEntity(request, supplier, admin);

        // Paso 3: Guardar Purchase (cascade ALL persiste los PurchaseItems)
        Purchase savedPurchase = purchaseRepository.save(purchase);

        // Paso 4: Actualizar stock de BulkProduct para items matcheados
        for (PurchaseItem item : savedPurchase.getItems()) {
            Long bulkProductId = item.getBulkProductId();
            if (bulkProductId != null) {
                bulkProductRepository.findById(bulkProductId).ifPresentOrElse(
                        bulkProduct -> {
                            BigDecimal increment = BigDecimal.valueOf(item.getQuantity());
                            BigDecimal newStock = bulkProduct.getCurrentStockLiters().add(increment);
                            bulkProduct.setCurrentStockLiters(newStock);
                            bulkProductRepository.save(bulkProduct);
                            log.info("Stock actualizado: BulkProduct {} +{} litros (nuevo stock: {})",
                                    bulkProductId, increment, newStock);
                        },
                        () -> log.warn("BulkProduct no encontrado para id: {} — item '{}' no actualiza stock",
                                bulkProductId, item.getDescription())
                );
            }
        }

        // Paso 5: Retornar PurchaseResponse
        return mapper.toResponse(savedPurchase);
    }
}
