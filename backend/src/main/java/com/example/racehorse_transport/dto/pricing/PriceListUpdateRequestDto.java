package com.example.racehorse_transport.dto.pricing;

import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.entity.enums.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PriceListUpdateRequestDto(@NotBlank @Size(max = 200) String name,
        @NotNull @Pattern(regexp = "[A-Z]{3}") String currencyCode,
        @NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer = 1, fraction = 4) BigDecimal marginRate,
        @NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer = 1, fraction = 4) BigDecimal fuelBufferRate,
        @NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer = 1, fraction = 4) BigDecimal depositRate,
        @NotNull LocalDate validFrom, LocalDate validTo) {
    public void applyTo(PriceList list) {
        list.setName(name.strip());
        list.setCurrencyCode(currencyCode);
        list.setValidFrom(validFrom);
        list.setValidTo(validTo);
    }
}
