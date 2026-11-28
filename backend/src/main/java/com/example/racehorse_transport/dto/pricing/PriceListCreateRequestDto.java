package com.example.racehorse_transport.dto.pricing;

import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.entity.enums.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PriceListCreateRequestDto(@NotBlank @Size(max = 200) String name,
        @NotNull @Pattern(regexp = "[A-Z]{3}") String currencyCode,
        @NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer = 1, fraction = 4) BigDecimal marginRate,
        @NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer = 1, fraction = 4) BigDecimal fuelBufferRate,
        @NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer = 1, fraction = 4) BigDecimal depositRate,
        @NotNull LocalDate validFrom, LocalDate validTo) {
    public PriceList toEntity() {
        PriceList list = new PriceList();
        new PriceListUpdateRequestDto(name, currencyCode, marginRate, fuelBufferRate, depositRate, validFrom, validTo)
                .applyTo(list);
        return list;
    }
}
