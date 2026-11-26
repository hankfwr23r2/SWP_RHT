package com.example.racehorse_transport.dto.pricing;

import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.entity.enums.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PriceItemResponseDto(Long id, Long priceListId, Long billableServiceId, Long routeId,
        Long vehicleClassId, String stallTypeCode, BigDecimal kmFrom, BigDecimal kmTo, BigDecimal unitPrice) {
    public static PriceItemResponseDto from(PriceItem item) {
        return new PriceItemResponseDto(item.getId(), item.getPriceList().getId(), item.getBillableService().getId(),
                item.getRoute() == null ? null : item.getRoute().getId(),
                item.getVehicleClass() == null ? null : item.getVehicleClass().getId(),
                item.getStallType() == null ? null : item.getStallType().getCode(),
                item.getKmFrom(), item.getKmTo(), item.getUnitPrice());
    }
}
