package com.example.racehorse_transport.dto.pricing;

import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.entity.enums.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PriceItemCreateRequestDto(@NotNull @Positive Long billableServiceId, @Positive Long routeId, @Positive Long vehicleClassId,
        @Size(min = 1, max = 30) String stallTypeCode,
        @DecimalMin("0") @Digits(integer = 6, fraction = 2) BigDecimal kmFrom,
        @DecimalMin("0") @Digits(integer = 6, fraction = 2) BigDecimal kmTo,
        @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal unitPrice) {
    public PriceItem toEntity(PriceList list, BillableService service, Route route, VehicleClass vehicleClass, StallType stallType) {
        PriceItem item = new PriceItem();
        item.setPriceList(list);
        new PriceItemUpdateRequestDto(billableServiceId, routeId, vehicleClassId, stallTypeCode, kmFrom, kmTo, unitPrice)
                .applyTo(item, service, route, vehicleClass, stallType);
        return item;
    }
}
