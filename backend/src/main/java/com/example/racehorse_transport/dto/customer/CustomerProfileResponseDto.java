package com.example.racehorse_transport.dto.customer;

import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.CustomerType;
import com.example.racehorse_transport.entity.enums.IdentityDocumentType;

public record CustomerProfileResponseDto(Long userId, String email, AppUserStatus accountStatus,
                                         CustomerType customerType, String fullName, String phone, String address,
                                         String countryCode, IdentityDocumentType identityDocumentType,
                                         String identityDocumentNumber, String identityDocumentCountryCode, String taxCode) {

    public static CustomerProfileResponseDto from(Customer customer) {
        var issuingCountry = customer.getIdentityDocumentCountry();
        return new CustomerProfileResponseDto(customer.getUserId(), customer.getUser().getEmail(),
                customer.getUser().getStatus(), customer.getCustomerType(), customer.getFullName(), customer.getPhone(),
                customer.getAddress(), customer.getCountry().getCode(), customer.getIdentityDocumentType(),
                customer.getIdentityDocumentNumber(), issuingCountry == null ? null : issuingCountry.getCode(),
                customer.getTaxCode());
    }
}
