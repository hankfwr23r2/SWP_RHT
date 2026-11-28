package com.example.racehorse_transport.dto.customer;

import com.example.racehorse_transport.entity.Country;
import com.example.racehorse_transport.entity.CustomerAddress;
import com.example.racehorse_transport.entity.Province;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CustomerAddressUpdateRequestDto(
        @NotBlank @Size(max = 100) String label,
        @Size(max = 500) String addressLine,
        @NotBlank @Size(max = 100) String ward,
        @NotBlank @Size(max = 10) String provinceCode,
        @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String countryCode,
        @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = 6) BigDecimal latitude,
        @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 6) BigDecimal longitude,
        Boolean defaultAddress) {

    public void applyTo(CustomerAddress address, Country country, Province province) {
        address.setLabel(label.strip());
        address.setAddressLine(addressLine == null || addressLine.isBlank() ? null : addressLine.strip());
        address.setWard(ward.strip());
        address.setCountry(country);
        address.setProvince(province);
        address.setLatitude(latitude);
        address.setLongitude(longitude);
    }
}
