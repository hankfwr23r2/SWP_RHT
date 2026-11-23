package com.example.racehorse_transport.dto.customer;

import com.example.racehorse_transport.entity.Country;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.enums.CustomerType;
import com.example.racehorse_transport.entity.enums.IdentityDocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerProfileUpdateRequestDto(
        @NotNull CustomerType customerType,
        @NotBlank @Size(max = 200) String fullName,
        @Size(max = 20) String phone,
        @Size(max = 500) String address,
        @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String countryCode,
        IdentityDocumentType identityDocumentType,
        @Size(max = 50) String identityDocumentNumber,
        @Pattern(regexp = "[A-Za-z]{2}") String identityDocumentCountryCode,
        @Size(max = 30) String taxCode) {

    public void applyTo(Customer customer, Country country, Country identityDocumentCountry) {
        customer.setCustomerType(customerType);
        customer.setFullName(fullName.strip());
        customer.setPhone(phone);
        customer.setAddress(address);
        customer.setCountry(country);
        customer.setIdentityDocumentType(identityDocumentType);
        customer.setIdentityDocumentNumber(identityDocumentNumber == null ? null : identityDocumentNumber.strip());
        customer.setIdentityDocumentCountry(identityDocumentCountry);
        customer.setTaxCode(taxCode);
    }
}
