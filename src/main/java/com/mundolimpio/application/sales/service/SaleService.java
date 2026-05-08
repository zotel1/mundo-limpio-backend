package com.mundolimpio.application.sales.service;

import com.mundolimpio.application.productionbatch.domain.ProductionBatch;
import com.mundolimpio.application.productionbatch.repository.ProductionBatchRepository;
import com.mundolimpio.application.sales.domain.Sale;
import com.mundolimpio.application.sales.domain.SaleItem;
import com.mundolimpio.application.sales.dto.SaleRequest;
import com.mundolimpio.application.sales.dto.SaleResponse;
import com.mundolimpio.application.sales.mapper.SaleMapper;
import com.mundolimpio.application.sales.repository.SaleItemRepository;
import com.mundolimpio.application.sales.repository.SaleRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Servicio principal para el módulo de ventas.
 * 
 * POR QUÉ ESTA IMPLEMENTACIÓN:
 * - Es el corazón del sistema FIFO (First In, First Out): los lotes más antiguos
 *   se descuentan primero. Esto evita que productos viejos se queden venciendo
 *   en stock mientras se venden los nuevos.
 * - Usamos inyección por constructor (en vez de @Autowired en campos) porque es
 *   la práctica recomendada de Spring: permite inmutabilidad, facilita testing,
 *   y los campos pueden ser final.
 * - @Transactional en createSale() asegura que TODA la operación (crear venta,
 *   crear items, actualizar stock de lotes) sea atómica: o funciona todo o nada.
 *   Si falla en el medio, se hace rollback y el stock no queda inconsistente.
 * - Logger SLF4J para auditoría: cada venta queda registrada en logs con los
 *   detalles para debugging y trazabilidad.
 */
@Service
public class SaleService {

    private static final Logger log = LoggerFactory.getLogger(SaleService.class);

    // Dependencias inyectadas por constructor (mejor práctica que @Autowired en campos)
    // Inmutables porque no se reasignan después de la construcción
    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final ProductionBatchRepository productionBatchRepository;
    private final SaleMapper saleMapper;

    /**
     * Constructor con inyección de dependencias.
     * Spring automáticamente resuelve e inyecta las instancias correctas.
     */
    public SaleService(SaleRepository saleRepository,
                       SaleItemRepository saleItemRepository,
                       ProductionBatchRepository productionBatchRepository,
                       SaleMapper saleMapper) {
        this.saleRepository = saleRepository;
        this.saleItemRepository = saleItemRepository;
        this.productionBatchRepository = productionBatchRepository;
        this.saleMapper = saleMapper;
    }

    /**
     * Crea una nueva venta aplicando lógica FIFO.
     * 
     * CÓMO FUNCIONA EL FIFO:
     * 1. Busca todos los lotes del producto con stock disponible, ordenados por
     *    fecha de producción ascendente (los más viejos primero).
     * 2. Calcula el stock total disponible para validar que alcanza.
     * 3. Si no hay suficiente stock, lanza IllegalArgumentException.
     * 4. Si hay stock, itera los lotes en orden FIFO restando cantidad de cada uno
     *    hasta completar lo solicitado.
     * 5. Crea un SaleItem por cada lote del que se descuenta stock.
     * 6. Actualiza el currentStock de cada lote afectado.
     * 
     * POR QUÉ @Transactional:
     * Sin esta anotación, cada save() sería una transacción separada. Si falla a
     * mitad de camino, tendrías una venta sin items o stock descontado sin venta.
     * Con @Transactional, todo se commitea junto o se hace rollback.
     * 
     * POR QUÉ OptimisticLockingFailureException:
     * Si dos pedidos concurrentes intentan descontar del mismo lote, @Version en
     * ProductionBatch detecta la colisión y lanza esta excepción. La capturamos
     * aquí para retornar un mensaje amigable en vez de un 500 genérico.
     * 
     * @param request DTO con productId y quantity solicitada
     * @return SaleResponse con los datos de la venta creada
     */
    @Transactional
    public SaleResponse createSale(SaleRequest request) {
        try {
            log.info("Creating sale for productId: {}, quantity: {}", request.productId(), request.quantity());

            // PASO 1: Obtener lotes con stock disponible en orden FIFO (más antiguo primero).
            // POR QUÉ OrderByProductionDateAsc: El lote más viejo se vende primero
            // para evitar que productos se venzan en stock.
            List<ProductionBatch> batches = productionBatchRepository
                    .findByProductIdAndCurrentStockGreaterThanOrderByProductionDateAsc(
                            request.productId(), BigDecimal.ZERO);

            // PASO 2: Calcular stock total sumando todos los lotes disponibles.
            // Usamos reduce con BigDecimal porque stock puede ser decimal (ej: 2.5 litros).
            BigDecimal totalAvailable = batches.stream()
                    .map(ProductionBatch::getCurrentStock)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            log.debug("Total available stock: {}, requested: {}", totalAvailable, request.quantity());

            // PASO 3: Validar que haya stock suficiente ANTES de empezar a descontar.
            // Si no hacemos esta validación primero, podríamos descontar de algunos lotes
            // y quedarnos a mitad de camino sin stock suficiente.
            if (totalAvailable.compareTo(request.quantity()) < 0) {
                throw new IllegalArgumentException(
                        String.format("Insufficient stock. Available: %.2f, Requested: %.2f",
                                totalAvailable, request.quantity()));
            }

            // PASO 4: Crear la entidad Sale. El totalAmount se calcula multiplicando
            // las cantidades por el costo unitario de cada lote (ver calculateTotalAmount).
            Sale sale = new Sale(calculateTotalAmount(batches, request.quantity()));
            sale = saleRepository.save(sale);

            // PASO 5: Aplicar deducción FIFO - iterar lotes y descontar stock.
            // remainingQuantity trackea cuánto nos falta descontar en total.
            BigDecimal remainingQuantity = request.quantity();
            List<SaleItem> saleItems = new ArrayList<>();

            for (ProductionBatch batch : batches) {
                // Si ya descontamos todo lo que necesitábamos, paramos.
                // Esto evita procesar lotes innecesarios.
                if (remainingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }

                // Cuánto stock tiene este lote y cuánto podemos tomar de él.
                // Usamos min() porque puede que solo necesitemos una parte del stock del lote.
                // Ej: lote tiene 10, necesitamos 6 → tomamos 6 de este lote.
                // Ej: lote tiene 3, necesitamos 6 → tomamos 3 de este lote, 3 del siguiente.
                BigDecimal batchStock = batch.getCurrentStock();
                BigDecimal quantityFromBatch = remainingQuantity.min(batchStock);

                // Crear el SaleItem con los datos snapshot del lote en este momento.
                // unitPriceAtSale y unitCostAtSale son el costo del lote en el momento
                // de la venta (no cambian aunque el costo del lote cambie después).
                SaleItem item = new SaleItem(
                        batch.getId(),
                        quantityFromBatch.intValue(),
                        batch.getUnitCostAtProduction(),  // precio de venta (actualmente = costo)
                        batch.getUnitCostAtProduction()   // costo unitario
                );
                item.setSale(sale);
                sale.addItem(item);
                saleItemRepository.save(item);

                // Actualizar el stock del lote: restamos lo que vendimos.
                // El @Version en ProductionBatch detectará si otro proceso modificó
                // este lote simultáneamente (optimistic locking).
                batch.setCurrentStock(batchStock.subtract(quantityFromBatch));
                remainingQuantity = remainingQuantity.subtract(quantityFromBatch);

                log.debug("Deducted {} from batch {}, remaining to deduct: {}",
                        quantityFromBatch, batch.getId(), remainingQuantity);
            }

            // PASO 6: Guardar la venta actualizada (con items asociados por cascade)
            // y retornar la respuesta mapeada a DTO.
            Sale savedSale = saleRepository.save(sale);
            log.info("Sale created successfully with ID: {}", savedSale.getId());

            return saleMapper.toResponse(savedSale);
        } catch (OptimisticLockingFailureException e) {
            // Conflicto de concurrencia: otro proceso modificó un lote mientras
            // procesábamos esta venta. Spring maneja esta excepción en GlobalExceptionHandler
            // retornando 409 Conflict. La relanzamos para que el handler la capture.
            log.error("Optimistic lock error during sale creation: {}", e.getMessage());
            throw e; // Relanzamos para que GlobalExceptionHandler la maneje → 409
        }
    }

    /**
     * Calcula el monto total de una venta usando los costos de los lotes en orden FIFO.
     * 
     * POR QUÉ método separado:
     * - Necesitamos el total ANTES de crear la venta (el constructor de Sale lo requiere).
     * - Reutilizamos la misma lógica de iteración FIFO que usamos para crear los items.
     * - Separación de responsabilidades: createSale orquesta, calculateTotalAmount calcula.
     * 
     * @param batches Lista de lotes en orden FIFO (ya filtrados y ordenados)
     * @param quantity Cantidad total solicitada
     * @return Monto total = suma de (cantidad × costo) por cada lote afectado
     */
    private BigDecimal calculateTotalAmount(List<ProductionBatch> batches, BigDecimal quantity) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal remaining = quantity;

        for (ProductionBatch batch : batches) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            BigDecimal batchStock = batch.getCurrentStock();
            BigDecimal quantityFromBatch = remaining.min(batchStock);
            // Multiplicamos cantidad × costo del lote para obtener el subtotal
            BigDecimal itemTotal = quantityFromBatch.multiply(batch.getUnitCostAtProduction());
            total = total.add(itemTotal);
            remaining = remaining.subtract(quantityFromBatch);
        }

        return total;
    }
}
