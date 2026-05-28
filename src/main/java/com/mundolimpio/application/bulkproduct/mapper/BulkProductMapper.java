package com.mundolimpio.application.bulkproduct.mapper;


import com.mundolimpio.application.bulkproduct.domain.BulkProduct;
import com.mundolimpio.application.bulkproduct.dto.BulkProductRequest;
import com.mundolimpio.application.bulkproduct.dto.BulkProductResponse;
import org.springframework.stereotype.Component;
/**
 * Mapper para convertire entre BulkProduct (entidad) y DTOs.
 * Uso manual (sin MapStruct) para mayor control y claridad.
**/


@Component
public class BulkProductMapper {

    /**
     * Convierte un BulkProductRequest a una entidad BulkProduct nueva.
     * */

    public BulkProduct toEntity(BulkProductRequest request) {
        BulkProduct entity = new BulkProduct(
                null,
                request.name(),
                request.currentStockLiters(),
                request.costPerLiter(),
                request.conversionRatio()
        );
        entity.setActive(true); // Por defecto, la materia prima se crea activa
        return entity;
    }

    /**
    * Actualiza una entidad existente con los datos del request.
     * */

    public void updateEntityFromRequest(BulkProduct entity, BulkProductRequest request) {
        if (request.name() != null) {
            entity.setName(request.name());
        }
        if (request.currentStockLiters() != null) {
            entity.setCurrentStockLiters(request.currentStockLiters());
        }
        if (request.costPerLiter() != null) {
            entity.setCostPerLiter(request.costPerLiter());
        }
        if (request.conversionRatio() != null) {
            entity.setConversionRatio(request.conversionRatio());
        }
    }

    /**
     * Convierte una entidad BulkProduct a un BulkProductResponse.
     * **/

    public BulkProductResponse toResponse(BulkProduct entity) {
        return new BulkProductResponse(
                entity.getId(),
                entity.getName(),
                entity.getCurrentStockLiters(),
                entity.getCostPerLiter(),
                entity.getConversionRatio(),
                entity.getActive()
        );
    }


}
