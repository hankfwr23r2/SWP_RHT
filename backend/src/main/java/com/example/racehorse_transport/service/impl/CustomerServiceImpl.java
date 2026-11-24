package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.customer.CustomerAddressCreateRequestDto;
import com.example.racehorse_transport.dto.customer.CustomerAddressResponseDto;
import com.example.racehorse_transport.dto.customer.CustomerAddressUpdateRequestDto;
import com.example.racehorse_transport.dto.customer.CustomerProfileResponseDto;
import com.example.racehorse_transport.dto.customer.CustomerProfileUpdateRequestDto;
import com.example.racehorse_transport.entity.Country;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.CustomerAddress;
import com.example.racehorse_transport.entity.Province;
import com.example.racehorse_transport.exception.CustomerRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.CustomerProfilePolicy;
import com.example.racehorse_transport.repository.AppUserRepository;
import com.example.racehorse_transport.repository.CountryRepository;
import com.example.racehorse_transport.repository.CustomerAddressRepository;
import com.example.racehorse_transport.repository.CustomerRepository;
import com.example.racehorse_transport.repository.ProvinceRepository;
import com.example.racehorse_transport.service.CustomerService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customers;
    private final CustomerAddressRepository addresses;
    private final AppUserRepository users;
    private final CountryRepository countries;
    private final ProvinceRepository provinces;
    private final SystemConfigService config;

    @Override
    @Transactional(readOnly = true)
    public CustomerProfileResponseDto customerProfile(String username) {
        return CustomerProfileResponseDto.from(findProfile(username));
    }

    @Override
    @Transactional
    public CustomerProfileResponseDto updateCustomerProfile(String username, CustomerProfileUpdateRequestDto request) {
        CustomerProfilePolicy.requireCompleteIdentity(request.identityDocumentType(),
                request.identityDocumentNumber(), request.identityDocumentCountryCode());
        Customer customer = lockProfile(username);
        Country issuingCountry = request.identityDocumentCountryCode() == null
                ? null : country(request.identityDocumentCountryCode());
        request.applyTo(customer, country(request.countryCode()), issuingCountry);
        try {
            customers.flush();
        } catch (DataIntegrityViolationException ex) {
            if (ex.getCause() instanceof ConstraintViolationException violation
                    && "uk_customer_identity".equals(violation.getConstraintName())) {
                throw new CustomerRuleException.DuplicateIdentityException();
            }
            throw ex;
        }
        return CustomerProfileResponseDto.from(customer);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerAddressResponseDto> customerAddresses(String username) {
        long customerId = findProfile(username).getUserId();
        return addresses.findByCustomer_UserIdOrderById(customerId).stream()
                .map(CustomerAddressResponseDto::from).toList();
    }

    @Override
    @Transactional
    public CustomerAddressResponseDto createCustomerAddress(String username, CustomerAddressCreateRequestDto request) {
        CustomerProfilePolicy.requireCoordinatesTogether(request.latitude(), request.longitude());
        Customer customer = lockProfile(username);
        Country country = country(request.countryCode());
        Province province = province(request.provinceCode(), country);
        requireNearProvince(request.latitude(), request.longitude(), province);
        var address = request.toEntity(customer, country, province);
        applyDefault(address, customer.getUserId(), request.defaultAddress());
        return CustomerAddressResponseDto.from(addresses.save(address));
    }

    @Override
    @Transactional
    public CustomerAddressResponseDto updateCustomerAddress(String username, Long id, CustomerAddressUpdateRequestDto request) {
        CustomerProfilePolicy.requireCoordinatesTogether(request.latitude(), request.longitude());
        long customerId = lockProfile(username).getUserId();
        CustomerAddress address = ownedAddress(id, customerId);
        Country country = country(request.countryCode());
        Province province = province(request.provinceCode(), country);
        requireNearProvince(request.latitude(), request.longitude(), province);
        request.applyTo(address, country, province);
        applyDefault(address, customerId, request.defaultAddress());
        return CustomerAddressResponseDto.from(address);
    }

    @Override
    @Transactional
    public void deleteCustomerAddress(String username, Long id) {
        addresses.delete(ownedAddress(id, lockProfile(username).getUserId()));
    }

    private void applyDefault(CustomerAddress address, long customerId, Boolean selected) {
        if (selected == null || selected.equals(address.getDefaultAddress())) {
            return;
        }
        if (selected) {
            addresses.findByCustomer_UserIdAndDefaultAddressTrue(customerId)
                    .filter(previous -> !Objects.equals(previous.getId(), address.getId()))
                    .ifPresent(previous -> {
                        previous.setDefaultAddress(false);
                        addresses.flush();
                    });
        }
        address.setDefaultAddress(selected);
    }

    private CustomerAddress ownedAddress(Long id, Long customerId) {
        return addresses.findByIdAndCustomer_UserId(id, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa chỉ."));
    }

    private Customer findProfile(String username) {
        return customers.findByUser_Username(username).orElseThrow(this::missingProfile);
    }

    private Customer lockProfile(String username) {
        long id = users.findByUsername(username).orElseThrow(this::missingProfile).getId();
        return customers.findForUpdate(id).orElseThrow(this::missingProfile);
    }

    private Country country(String code) {
        return countries.findById(code.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new CustomerRuleException("Quốc gia không hợp lệ."));
    }

    private Province province(String code, Country country) {
        Province province = provinces.findById(code.strip().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new CustomerRuleException("Tỉnh không hợp lệ."));
        CustomerProfilePolicy.requireAddressCountry(country.getCode(), province.getCountry().getCode());
        return province;
    }

    private void requireNearProvince(BigDecimal latitude, BigDecimal longitude, Province province) {
        CustomerProfilePolicy.requirePointNearProvince(latitude, longitude, province.getLatitude(), province.getLongitude(),
                config.getInt("ADDRESS_MAX_KM_FROM_PROVINCE"));
    }

    private ResourceNotFoundException missingProfile() {
        return new ResourceNotFoundException("Không tìm thấy hồ sơ khách.");
    }
}
