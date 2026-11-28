package com.example.racehorse_transport.dto.customer;

import com.example.racehorse_transport.entity.CustomerAddress;
import java.math.BigDecimal;

public record CustomerAddressResponseDto(Long id, String label, String addressLine, String ward, String provinceCode,
                                         String countryCode, BigDecimal latitude, BigDecimal longitude,
                                         boolean defaultAddress) {

    public static CustomerAddressResponseDto from(CustomerAddress address) {
        return new CustomerAddressResponseDto(address.getId(), address.getLabel(), address.getAddressLine(),
                address.getWard(), address.getProvince().getCode(), address.getCountry().getCode(),
                address.getLatitude(), address.getLongitude(), Boolean.TRUE.equals(address.getDefaultAddress()));
    }
}
