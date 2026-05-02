package com.mundolimpio.application.product.service;

import com.mundolimpio.application.common.exception.ProductAlreadyExistsException;
import com.mundolimpio.application.common.exception.ProductNotFoundException;
import com.mundolimpio.application.product.domain.Product;
import com.mundolimpio.application.product.dto.ProductRequest;
import com.mundolimpio.application.product.dto.ProductResponse;
import com.mundolimpio.application.product.mapper.ProductMapper;
import com.mundolimpio.application.product.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * ProductService contiene la lógica de negocio para el módulo de productos.
 *
 * Responsabilidades:
 * - Validaciones de negocio (unicidad de SKU)
 * - Conversión entre DTOs y Entities (delegando a ProductMapper)
 * - Transacciones ACID (@Transactional)
 * - Logging de operaciones
 *
 * @Transactional garantiza que todas las operaciones sean atómicas.
 * Si algo falla, todo se revierte automáticamente.
 */
@Service
@Transactional
public class ProductService {

    // Logger para auditoría y debugging
    private static final Logger logger = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    /**
     * Constructor con inyección de dependencias.
     * ProductRepository: acceso a datos
     * ProductMapper: conversión Entity ↔ DTOs
     */
    public ProductService(ProductRepository productRepository, ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.productMapper = productMapper;
    }

    // ========================= CREATE =========================

    /**
     * Crea un nuevo producto.
     *
     * Validaciones:
     * - El SKU debe ser único (si no, lanza ProductAlreadyExistsException)
     * - Los datos son validados por @Valid en el Controller
     *
     * @param request El ProductRequest con los datos del nuevo producto
     * @return ProductResponse con el producto creado (incluyendo ID generado)
     * @throws ProductAlreadyExistsException si el SKU ya existe
     */
    public ProductResponse createProduct(ProductRequest request) {
        logger.info("Creating product with SKU: {}", request.sku());

        // Validación: verificar que el SKU no exista
        if (productRepository.existsBySku(request.sku())) {
            logger.warn("Attempted to create product with duplicate SKU: {}", request.sku());
            throw new ProductAlreadyExistsException(request.sku());
        }

        // Conversión: ProductRequest → Product (Entity)
        Product product = productMapper.toEntity(request);

        // Persistencia: guardar en la base de datos
        Product savedProduct = productRepository.save(product);
        logger.info("Product created successfully with ID: {} and SKU: {}", savedProduct.getId(), savedProduct.getSku());

        // Conversión: Product → ProductResponse
        return productMapper.toResponse(savedProduct);
    }

    // ========================= READ =========================

    /**
     * Obtiene un producto por su ID.
     *
     * @param id El identificador único del producto
     * @return ProductResponse con los datos del producto encontrado
     * @throws ProductNotFoundException si el ID no existe
     */
    public ProductResponse getProductById(Long id) {
        logger.info("Fetching product with ID: {}", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> {
                    logger.warn("Product not found with ID: {}", id);
                    return new ProductNotFoundException("ID: " + id);
                });

        logger.info("Product found with ID: {} (SKU: {})", id, product.getSku());
        return productMapper.toResponse(product);
    }

    /**
     * Obtiene un producto por su SKU.
     *
     * @param sku El identificador único del producto
     * @return ProductResponse con los datos del producto encontrado
     * @throws ProductNotFoundException si el SKU no existe
     */
    public ProductResponse getProductBySku(String sku) {
        logger.info("Fetching product with SKU: {}", sku);

        Product product = productRepository.findBySku(sku)
                .orElseThrow(() -> {
                    logger.warn("Product not found with SKU: {}", sku);
                    return new ProductNotFoundException(sku);
                });

        logger.info("Product found with SKU: {} (ID: {})", sku, product.getId());
        return productMapper.toResponse(product);
    }

    /**
     * Obtiene todos los productos activos.
     * Útil para listados, reportes e inventario FIFO.
     *
     * @return Lista de ProductResponse de productos activos
     */
    public List<ProductResponse> getAllActiveProducts() {
        logger.info("Fetching all active products");

        List<ProductResponse> activeProducts = productRepository.findByActiveTrue()
                .stream()
                .map(productMapper::toResponse)
                .toList();

        logger.info("Found {} active products", activeProducts.size());
        return activeProducts;
    }

    /**
     * Obtiene todos los productos (activos e inactivos).
     *
     * @return Lista de todos los ProductResponse
     */
    public List<ProductResponse> getAllProducts() {
        logger.info("Fetching all products (active and inactive)");

        List<ProductResponse> allProducts = productRepository.findAll()
                .stream()
                .map(productMapper::toResponse)
                .toList();

        logger.info("Found {} total products", allProducts.size());
        return allProducts;
    }

    // ========================= UPDATE =========================

    /**
     * Actualiza un producto existente.
     *
     * Validaciones:
     * - El producto debe existir (si no, lanza ProductNotFoundException)
     * - Si cambia el SKU, debe ser único (si no, lanza ProductAlreadyExistsException)
     *
     * @param id El ID del producto a actualizar
     * @param request El ProductRequest con los nuevos datos
     * @return ProductResponse con el producto actualizado
     * @throws ProductNotFoundException si el ID no existe
     * @throws ProductAlreadyExistsException si el nuevo SKU ya existe en otro producto
     */
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        logger.info("Updating product with ID: {}", id);

        // Obtener el producto existente
        Product product = productRepository.findById(id)
                .orElseThrow(() -> {
                    logger.warn("Attempted to update non-existent product with ID: {}", id);
                    return new ProductNotFoundException("ID: " + id);
                });

        // Validar que el nuevo SKU sea único (si es diferente)
        if (!product.getSku().equals(request.sku()) && productRepository.existsBySku(request.sku())) {
            logger.warn("Attempted to update product with duplicate SKU: {}", request.sku());
            throw new ProductAlreadyExistsException(request.sku());
        }

        // Actualizar los datos
        product.setSku(request.sku());
        product.setName(request.name());
        product.setMinPrice(request.minPrice());

        // Persistir cambios
        Product updatedProduct = productRepository.save(product);
        logger.info("Product updated successfully. ID: {}, new SKU: {}", id, updatedProduct.getSku());

        return productMapper.toResponse(updatedProduct);
    }

    // ========================= DELETE =========================

    /**
     * Realiza un SOFT DELETE del producto (marca como inactivo).
     * No elimina el registro de la BD, solo marca active = false.
     *
     * Razón: Importante para mantener integridad referencial con
     * production_batches y sales (trazabilidad FIFO).
     *
     * @param id El ID del producto a eliminar (soft delete)
     * @throws ProductNotFoundException si el ID no existe
     */
    public void deleteProductSoftDelete(Long id) {
        logger.info("Performing soft delete on product with ID: {}", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> {
                    logger.warn("Attempted to delete non-existent product with ID: {}", id);
                    return new ProductNotFoundException("ID: " + id);
                });

        // Soft delete: marcar como inactivo
        product.setActive(false);
        productRepository.save(product);
        logger.info("Product soft deleted (marked as inactive). ID: {}, SKU: {}", id, product.getSku());
    }

    /**
     * Reactiva un producto marcado como inactivo.
     * Reversa de un soft delete.
     *
     * @param id El ID del producto a reactivar
     * @throws ProductNotFoundException si el ID no existe
     */
    public void reactivateProduct(Long id) {
        logger.info("Reactivating product with ID: {}", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> {
                    logger.warn("Attempted to reactivate non-existent product with ID: {}", id);
                    return new ProductNotFoundException("ID: " + id);
                });

        product.setActive(true);
        productRepository.save(product);
        logger.info("Product reactivated. ID: {}, SKU: {}", id, product.getSku());
    }

    /**
     * Elimina permanentemente un producto de la base de datos.
     *
     * ⚠️ CUIDADO: Esto borra el registro completamente.
     * Solo usar si NO hay relaciones con production_batches o sales.
     * De lo contrario, violarás constraints de Foreign Key.
     *
     * @param id El ID del producto a eliminar permanentemente
     * @throws ProductNotFoundException si el ID no existe
     */
    public void deleteProductPermanent(Long id) {
        logger.warn("Performing PERMANENT DELETE on product with ID: {}", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> {
                    logger.warn("Attempted to permanently delete non-existent product with ID: {}", id);
                    return new ProductNotFoundException("ID: " + id);
                });

        productRepository.deleteById(id);
        logger.warn("Product PERMANENTLY DELETED. ID: {}, SKU: {}", id, product.getSku());
    }
}