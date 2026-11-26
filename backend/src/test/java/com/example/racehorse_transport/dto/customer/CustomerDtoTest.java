package com.example.racehorse_transport.dto.customer;

import com.example.racehorse_transport.entity.AppUser;
import com.example.racehorse_transport.entity.Country;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.Province;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.CustomerType;
import com.example.racehorse_transport.entity.enums.IdentityDocumentType;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CustomerDtoTest {

    @Test
    void updatesProfileWhilePreservingAccountAndMapsResponse() {
        var user = new AppUser();
        user.setEmail("customer@example.com");
        user.setStatus(AppUserStatus.ACTIVE);
        var customer = new Customer();
        customer.setUserId(10L);
        customer.setUser(user);
        var country = new Country();
        country.setCode("VN");
        new CustomerProfileUpdateRequestDto(CustomerType.PERSON, " Customer ", "123", null, "VN",
                IdentityDocumentType.PASSPORT, " P1 ", "VN", null).applyTo(customer, country, country);

        assertThat(customer.getUser()).isSameAs(user);
        assertThat(CustomerProfileResponseDto.from(customer)).isEqualTo(new CustomerProfileResponseDto(
                10L, "customer@example.com", AppUserStatus.ACTIVE, CustomerType.PERSON, "Customer", "123", null,
                "VN", IdentityDocumentType.PASSPORT, "P1", "VN", null));
    }

    @Test
    void mapsAddressAndLeavesOwnerAndDefaultUnderServerControl() {
        var customer = new Customer();
        var country = new Country();
        country.setCode("VN");
        var province = new Province();
        province.setCode("VN-79");
        var request = new CustomerAddressCreateRequestDto(" Home ", " Address ", "Phường Bến Nghé", "VN-79", "VN", null, null, true);
        var address = request.toEntity(customer, country, province);
        assertThat(address.getId()).isNull();
        assertThat(address.getDefaultAddress()).isFalse();
        address.setId(9L);
        address.setDefaultAddress(true);
        new CustomerAddressUpdateRequestDto(" Farm ", "New address", "Phường Bến Nghé", "VN-79", "VN", null, null, false)
                .applyTo(address, country, province);
        assertThat(address.getCustomer()).isSameAs(customer);
        assertThat(CustomerAddressResponseDto.from(address)).isEqualTo(
                new CustomerAddressResponseDto(9L, "Farm", "New address", "Phường Bến Nghé", "VN-79", "VN", null, null, true));
    }
}
