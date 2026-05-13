package com.mundolimpio.application.inventory.service;

import com.mundolimpio.application.inventory.domain.Inventory;
import com.mundolimpio.application.inventory.domain.InventoryAdjustment;
import com.mundolimpio.application.inventory.dto.AdjustmentRequest;
import com.mundolimpio.application.inventory.dto.InventoryResponse;
import com.mundolimpio.application.inventory.exception.InvalidAdjustmentException;
import com.mundolimpio.application.inventory.exception.InventoryNotFoundException;
import com.mundolimpio.application.inventory.mapper.InventoryMapper;
import com.mundolimpio.application.inventory.repository.InventoryAdjustmentRepository;
import com.mundolimpio.application.inventory.repository.InventoryRepository;
import com.mundolimpio.application.product.domain.Product;
import com.mundolimpio.application.product.repository.ProductRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Servicio principal para el módulo de inventario.
 *
 * QUE HACE: Gestiona consultas y modificaciones de stock para cada producto.
 * Provee operaciones de consulta (getInventory, getLowStockInventories),
 * ajuste manual con auditoría (adjustStock), y métodos de integración
 * package-private (incrementStock, decrementStock) para ser usados desde
 * ProductionBatchService y SaleService respectivamente.
 *
 * POR QUE: Separamos la lógica de negocio de inventario del controlador
 * para mantener la capa de presentación delgada. El servicio orquesta
 * las reglas de negocio, validaciones y persistencia.
 *
 * DIFERENCIA con SaleService:
 *   - SaleService usa FIFO (distribuye entre lotes).
 *   - InventoryService opera sobre un único valor por producto (1:1).
 *   - SaleService tiene lógica compleja de distribución entre lotes.
 *   - InventoryService tiene reglas de negocio simples pero con auditoría.
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryRepository inventoryRepository;
    private final InventoryAdjustmentRepository adjustmentRepository;
    private final ProductRepository productRepository;
    private final InventoryMapper inventoryMapper;

    /**
     * Constructor con inyección de dependencias.
     * Incluye ProductRepository porque incrementStock necesita crear
     * un Inventory nuevo (con proxy de Product) si el producto aún
     * no tiene inventario registrado.
     */
    public InventoryService(InventoryRepository inventoryRepository,
                            InventoryAdjustmentRepository adjustmentRepository,
                            ProductRepository productRepository,
                            InventoryMapper inventoryMapper) {
        this.inventoryRepository = inventoryRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.productRepository = productRepository;
        this.inventoryMapper = inventoryMapper;
    }

    // ======================== QUERY METHODS ========================

    /**
     * Obtiene el inventario actual de un producto específico.
     *
     * QUE HACE: Busca el Inventory por productId y lo mapea a
     * InventoryResponse. Si no existe (el producto no tiene fila en
     * inventory), lanza InventoryNotFoundException → 404.
     *
     * @param productId ID del producto
     * @return InventoryResponse con stock actual y umbral mínimo
     * @throws InventoryNotFoundException si no existe inventario
     */
    public InventoryResponse getInventory(Long productId) {
        // Buscar inventario por productId
        // POR QUE orElseThrow: el Optional.empty() indica que no hay
        // inventario para ese producto. Lanzamos excepción en vez de
        // retornar null para que GlobalExceptionHandler la capture y
        // devuelva un 404 con body JSON estandarizado.
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new InventoryNotFoundException(productId));

        // Mapear entidad a DTO de respuesta
        // POR QUE usamos el mapper: separamos la lógica de conversión
        // de la lógica de negocio (Single Responsibility).
        return inventoryMapper.toResponse(inventory);
    }

    /**
     * Obtiene todos los productos con stock por debajo de su umbral mínimo.
     *
     * QUE HACE: Consulta los inventarios donde currentStock < minStockThreshold
     * usando una @Query JPQL en el repositorio (no method derivation, porque
     * la comparación es entre dos columnas de la misma entidad).
     *
     * @return Lista de InventoryResponse con stock bajo
     */
    public List<InventoryResponse> getLowStockInventories() {
        // findLowStockInventories() usa @Query para comparar currentStock
        // contra minStockThreshold de cada fila individualmente.
        return inventoryRepository.findLowStockInventories()
                .stream()
                .map(inventoryMapper::toResponse)
                .toList();
    }

    // ======================== MANUAL ADJUSTMENT ========================

    /**
     * Ajusta manualmente el stock de un producto con registro de auditoría.
     *
     * QUE HACE:
     * 1. Busca el Inventory del producto (lanza 404 si no existe).
     * 2. Calcula nuevo stock: currentStock + quantity.
     *    POR QUE quantity usa convención de signo:
     *    - Positivo = aumenta stock (ej: +5 suma 5 unidades).
     *    - Negativo = disminuye stock (ej: -5 resta 5 unidades).
     *    Esto evita un campo direction separado y simplifica el DTO.
     * 3. Valida que el nuevo stock no sea negativo (regla de negocio:
     *    el stock físico no puede ser negativo).
     * 4. Actualiza y guarda Inventory. @Version en la entidad detecta
     *    colisiones de concurrencia (dos ajustes simultáneos).
     * 5. Crea y guarda InventoryAdjustment como trail de auditoría.
     *    POR QUE guardamos auditoría AQUÍ y no en increment/decrement:
     *    - adjustStock es una acción manual del ADMIN que siempre
     *      necesita justificación (type + reason).
     *    - increment/decrement son automáticos (integración con
     *      producción/ventas) y su auditoría está en los propios
     *      eventos de negocio (ProductionBatch, Sale).
     *
     * POR QUE @Transactional:
     * - La actualización de Inventory y la creación del audit trail
     *   deben ser ATÓMICAS: si falla una, se revierte la otra.
     * - Sin @Transactional, si falla adjustmentRepository.save(),
     *   el inventory.save() ya se habría commiteado → inconsistencia.
     *
     * @param productId ID del producto a ajustar
     * @param request   DTO con type, quantity (con signo) y reason
     * @return InventoryResponse con stock actualizado
     * @throws InventoryNotFoundException si el producto no tiene inventario
     * @throws InvalidAdjustmentException si el ajuste deja stock negativo
     */
    @Transactional
    public InventoryResponse adjustStock(Long productId, AdjustmentRequest request) {
        // PASO 1: Obtener inventario actual
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new InventoryNotFoundException(productId));

        // PASO 2: Calcular nuevo stock usando quantity con signo
        // POR QUE usamos add(): si quantity es negativo, BigDecimal.add()
        // maneja correctamente la suma algebraica.
        // Ej: currentStock=10 + quantity=-5 → newStock=5
        // Ej: currentStock=10 + quantity=+3 → newStock=13
        BigDecimal newStock = inventory.getCurrentStock().add(request.quantity());

        // PASO 3: Validar que el stock no quede negativo
        // POR QUE validamos en el servicio y NO en el DTO:
        // - El DTO (@PositiveOrZero en quantity) impediría valores
        //   negativos, pero los valores negativos SON válidos (decremento).
        // - El servicio conoce el stock actual; el DTO no.
        // - La regla "stock no negativo" es una regla de negocio que
        //   depende del estado actual, no de la validación del input.
        if (newStock.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidAdjustmentException(
                    "Insufficient stock: cannot adjust below zero. " +
                    "Current stock: " + inventory.getCurrentStock() +
                    ", adjustment: " + request.quantity());
        }

        // PASO 4: Actualizar stock y persistir
        // POR QUE @Version maneja concurrencia (optimistic locking):
        // - Si dos ADMINs ajustan el mismo producto simultáneamente,
        //   el primer save() incrementa version en la BD.
        // - El segundo save() detecta que su version está desactualizada
        //   y lanza OptimisticLockingFailureException → 409 Conflict.
        // - Esto evita que un ajuste sobrescriba silenciosamente al otro.
        // - Alternativa: pesimistic lock (SELECT ... FOR UPDATE) pero
        //   requiere sintaxis específica de BD y retiene locks por más tiempo.
        inventory.setCurrentStock(newStock);
        inventoryRepository.save(inventory);

        // PASO 5: Crear y guardar audit trail
        // POR QUE siempre guardamos: aunque la cantidad sea 0, el registro
        // de que se intentó un ajuste puede ser útil para auditoría.
        InventoryAdjustment adjustment = new InventoryAdjustment(
                inventory,
                request.type(),
                request.quantity(),
                request.reason()
        );
        adjustmentRepository.save(adjustment);

        log.info("Stock adjusted for productId: {}. New stock: {}. Type: {}, Reason: {}",
                productId, newStock, request.type(), request.reason());

        return inventoryMapper.toResponse(inventory);
    }

    // ======================== INTEGRATION METHODS ========================

    /**
     * Incrementa el stock de un producto (package-private para integración).
     *
     * QUE HACE: Suma quantity al currentStock actual.
     * Si el producto aún no tiene fila en inventory, la crea con
     * currentStock = quantity (find-or-create pattern).
     *
     * POR QUE package-private (sin public):
     * - Solo debe llamarse desde ProductionBatchService dentro del
     *   mismo @Transactional al crear un lote de producción.
     * - No debe exponerse como endpoint REST: los incrementos automáticos
     *   no pasan por adjustStock() porque no necesitan auditoría manual
     *   (la auditoría es el ProductionBatch mismo).
     * - Los clientes externos deben usar adjustStock() para cambios
     *   manuales con trail de auditoría completo.
     *
     * DIFERENCIA con adjustStock:
     * - adjustStock: público, con auditoría, recibe AdjustmentRequest.
     * - incrementStock: package-private, SIN auditoría, solo quantity.
     * - incrementStock usa find-or-create (por si el producto no tiene
     *   inventario al crearse el primer lote).
     *
     * @param productId ID del producto
     * @param quantity  Cantidad a incrementar (siempre positiva)
     */
    @Transactional
    void incrementStock(Long productId, BigDecimal quantity) {
        // Find-or-create: si no existe inventario, lo creamos con
        // Product como proxy (evita query adicional a la tabla products).
        // POR QUE usamos getReferenceById: devuelve un proxy de JPA sin
        // consultar la BD. Es suficiente para establecer la FK product_id.
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseGet(() -> {
                    Product productProxy = productRepository.getReferenceById(productId);
                    Inventory newInventory = new Inventory(productProxy, BigDecimal.ZERO);
                    return inventoryRepository.save(newInventory);
                });

        // Incrementar stock: currentStock += quantity
        inventory.setCurrentStock(inventory.getCurrentStock().add(quantity));
        inventoryRepository.save(inventory);

        log.debug("Incremented stock for productId: {} by {}. New stock: {}",
                productId, quantity, inventory.getCurrentStock());
    }

    /**
     * Decrementa el stock de un producto (package-private para integración).
     *
     * QUE HACE: Resta quantity del currentStock actual.
     * Si el stock quedaría negativo, lanza InvalidAdjustmentException.
     *
     * POR QUE package-private (sin public):
     * - Solo debe llamarse desde SaleService dentro del mismo @Transactional.
     * - Misma razón que incrementStock: no necesita endpoint REST propio.
     *
     * DIFERENCIA con incrementStock:
     * - incrementStock usa find-or-create (el producto puede no tener
     *   inventario al crearse el primer lote).
     * - decrementStock NO usa find-or-create: si no existe inventario,
     *   no hay stock que decrementar → lanza InventoryNotFoundException.
     *
     * @param productId ID del producto
     * @param quantity  Cantidad a decrementar (siempre positiva)
     * @throws InventoryNotFoundException si no existe inventario
     * @throws InvalidAdjustmentException si el stock quedaría negativo
     */
    @Transactional
    void decrementStock(Long productId, BigDecimal quantity) {
        // Buscar inventario existente
        // POR QUE NO usamos find-or-create: si no hay inventario, no
        // hay stock que decrementar. Es un error de integración.
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new InventoryNotFoundException(productId));

        // Calcular nuevo stock restando quantity
        // POR QUE usamos subtract() en vez de add(-quantity): la semántica
        // es más clara (estamos restando, no sumando un negativo).
        BigDecimal newStock = inventory.getCurrentStock().subtract(quantity);

        // Validar que el stock no quede negativo
        // POR QUE la misma regla que adjustStock: el stock no puede ser
        // negativo. Si el sistema de ventas intenta vender más de lo
        // que hay en inventory (aunque haya pasado la validación de
        // production_batches), esto es una inconsistencia que debe
        // detectarse y reportarse.
        if (newStock.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidAdjustmentException(
                    "Insufficient stock: cannot decrement below zero. " +
                    "Current stock: " + inventory.getCurrentStock() +
                    ", decrement: " + quantity);
        }

        inventory.setCurrentStock(newStock);
        inventoryRepository.save(inventory);

        log.debug("Decremented stock for productId: {} by {}. New stock: {}",
                productId, quantity, inventory.getCurrentStock());
    }
}
