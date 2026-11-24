package com.example.racehorse_transport.service;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.dto.customer.CustomerAddressCreateRequestDto;
import com.example.racehorse_transport.dto.customer.CustomerAddressUpdateRequestDto;
import com.example.racehorse_transport.dto.customer.CustomerProfileUpdateRequestDto;
import com.example.racehorse_transport.entity.enums.CustomerType;
import com.example.racehorse_transport.entity.enums.IdentityDocumentType;
import com.example.racehorse_transport.exception.CustomerRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.support.TestData;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@PostgresIntegrationTest
@Transactional
class CustomerServiceTest {

    @Autowired private CustomerService customers;
    @Autowired private TestData data;

    @Test
    void changesDefaultAddressAndCanDeleteIt() {
        String owner = customer();
        var first = customers.createCustomerAddress(owner, address("First", true));
        var second = customers.createCustomerAddress(owner, address("Second", true));

        assertThat(customers.customerAddresses(owner)).extracting(a -> a.defaultAddress()).containsExactly(false, true);
        var updated = customers.updateCustomerAddress(owner, first.id(), new CustomerAddressUpdateRequestDto(
                "Updated", "Address 1", null, "VN-79", "VN", null, null, true));
        assertThat(updated.defaultAddress()).isTrue();
        assertThat(customers.customerAddresses(owner)).extracting(a -> a.defaultAddress()).containsExactly(true, false);
        customers.deleteCustomerAddress(owner, first.id());
        assertThat(customers.customerAddresses(owner)).singleElement().satisfies(remaining -> {
            assertThat(remaining.id()).isEqualTo(second.id());
            assertThat(remaining.defaultAddress()).isFalse();
        });
    }

    @Test
    void rejectsOtherCustomerAddressAndMismatchedProvince() {
        String owner = customer();
        String other = customer();
        long addressId = customers.createCustomerAddress(owner, address("Private", false)).id();
        assertThatThrownBy(() -> customers.deleteCustomerAddress(other, addressId))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> customers.updateCustomerAddress(other, addressId, new CustomerAddressUpdateRequestDto(
                "Other", "Address", null, "VN-79", "VN", null, null, true))).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> customers.createCustomerAddress(owner, new CustomerAddressCreateRequestDto(
                "Wrong country", "Address", null, "VN-79", "KH", null, null, false))).isInstanceOf(CustomerRuleException.class);
        assertThat(customers.customerAddresses(other)).isEmpty();
        assertThat(customers.customerAddresses(owner)).hasSize(1);
    }

    @Test
    void rejectsPinFarFromProvinceWhenCreatingAndUpdating() {
        String owner = customer();
        BigDecimal farLat = new BigDecimal("15.505957");
        BigDecimal farLng = new BigDecimal("106.479492");
        assertThatThrownBy(() -> customers.createCustomerAddress(owner, new CustomerAddressCreateRequestDto(
                "Far", "Address", null, "VN-79", "VN", farLat, farLng, false))).isInstanceOf(CustomerRuleException.class);
        long id = customers.createCustomerAddress(owner, new CustomerAddressCreateRequestDto(
                "Near", "Address", null, "VN-79", "VN", new BigDecimal("10.973"), new BigDecimal("106.493"), false)).id();
        assertThatThrownBy(() -> customers.updateCustomerAddress(owner, id, new CustomerAddressUpdateRequestDto(
                "Near", "Address", null, "VN-79", "VN", farLat, farLng, false))).isInstanceOf(CustomerRuleException.class);
        assertThat(customers.customerAddresses(owner)).singleElement()
                .satisfies(saved -> assertThat(saved.latitude()).isEqualByComparingTo("10.973"));
    }

    @Test
    void editsOwnProfileAndRejectsIncompleteIdentity() {
        String owner = customer();
        var profile = new CustomerProfileUpdateRequestDto(CustomerType.ORGANIZATION, "Company name", null, null,
                "VN", IdentityDocumentType.BUSINESS_REG, "A2-PROFILE-TEST", "VN", "Tax number");
        assertThat(customers.updateCustomerProfile(owner, profile).fullName()).isEqualTo("Company name");
        assertThat(customers.customerProfile(owner).identityDocumentType()).isEqualTo(IdentityDocumentType.BUSINESS_REG);
        assertThatThrownBy(() -> customers.updateCustomerProfile(owner, new CustomerProfileUpdateRequestDto(
                CustomerType.PERSON, "Incomplete", null, null, "VN", IdentityDocumentType.PASSPORT, null, null, null)))
                .isInstanceOf(CustomerRuleException.class);
    }

    private String customer() {
        String username = data.unique("profile-a2");
        data.customer(username);
        return username;
    }

    private CustomerAddressCreateRequestDto address(String label, boolean defaultAddress) {
        return new CustomerAddressCreateRequestDto(label, "Address", null, "VN-79", "VN", null, null, defaultAddress);
    }
}
