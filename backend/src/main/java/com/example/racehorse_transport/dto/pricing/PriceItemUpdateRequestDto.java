package com.example.racehorse_transport.dto.pricing;

import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.entity.enums.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PriceItemUpdateRequestDto(@NotNull @Positive Long billableServiceId, @Positive Long routeId, @Positive Long vehicleClassId,
        @Size(min = 1, max = 30) String stallTypeCode,
        @DecimalMin("0") @Digits(integer = 6, fraction = 2) BigDecimal kmFrom,
        @DecimalMin("0") @Digits(integer = 6, fraction = 2) BigDecimal kmTo,
        @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal unitPrice) {
    public void applyTo(PriceItem item, BillableService service, Route route, VehicleClass vehicleClass, StallType stallType) {
        item.setBillableService(service);
        item.setRoute(route);
        item.setVehicleClass(vehicleClass);
        item.setStallType(stallType);
        item.setKmFrom(kmFrom);
        item.setKmTo(kmTo);
    }
}
