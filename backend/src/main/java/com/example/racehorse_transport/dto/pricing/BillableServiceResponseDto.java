package com.example.racehorse_transport.dto.pricing;

import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.entity.enums.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record BillableServiceResponseDto(Long id, String code, String name, ServiceLayer layer,
        BillingUnit billingUnit, boolean passThrough, boolean autoApplied, ActiveStatus status) {
    public static BillableServiceResponseDto from(BillableService service) {
        return new BillableServiceResponseDto(service.getId(), service.getCode(), service.getName(), service.getLayer(),
                service.getBillingUnit(), service.getPassThrough(), service.getAutoApplied(), service.getStatus());
    }
}
