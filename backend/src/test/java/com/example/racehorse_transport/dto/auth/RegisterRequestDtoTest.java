package com.example.racehorse_transport.dto.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.entity.AppUser;
import com.example.racehorse_transport.entity.Country;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.enums.CustomerType;
import org.junit.jupiter.api.Test;

class RegisterRequestDtoTest {

    private static RegisterRequestDto request() {
        RegisterRequestDto request = new RegisterRequestDto();
        request.setUsername(" khach ");
        request.setEmail(" Khach@Gmail.com ");
        request.setFullName(" Nguyễn Văn An ");
        request.setPhone(" 0909123456 ");
        request.setAddress("   ");
        request.setLanguage("en");
        return request;
    }

    @Test
    void accountLeavesRoleStatusAndVerificationToService() {
        AppUser user = request().toEntity("hash");

        assertThat(user.getUsername()).isEqualTo("khach");
        assertThat(user.getEmail()).isEqualTo("khach@gmail.com");
        assertThat(user.getPasswordHash()).isEqualTo("hash");
        assertThat(user.getPreferredLanguage()).isEqualTo("en");
        assertThat(user.getId()).isNull();
        assertThat(user.getRole()).isNull();
        assertThat(user.getStatus()).isNull();
        assertThat(user.getEmailVerified()).isFalse();
    }

    @Test
    void customerProfileTakesGivenAccountAndCountry() {
        AppUser user = new AppUser();
        Country country = new Country();

        Customer customer = request().toCustomer(user, country);

        assertThat(customer.getUserId()).isNull();
        assertThat(customer.getUser()).isSameAs(user);
        assertThat(customer.getCountry()).isSameAs(country);
        assertThat(customer.getCustomerType()).isEqualTo(CustomerType.PERSON);
        assertThat(customer.getFullName()).isEqualTo("Nguyễn Văn An");
        assertThat(customer.getPhone()).isEqualTo("0909123456");
        assertThat(customer.getAddress()).isNull();
        assertThat(customer.getIdentityDocumentType()).isNull();
        assertThat(customer.getTaxCode()).isNull();
    }
}
