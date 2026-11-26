package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.customer.CustomerAddressCreateRequestDto;
import com.example.racehorse_transport.dto.customer.CustomerAddressResponseDto;
import com.example.racehorse_transport.dto.customer.CustomerAddressUpdateRequestDto;
import com.example.racehorse_transport.dto.customer.CustomerProfileResponseDto;
import com.example.racehorse_transport.dto.customer.CustomerProfileUpdateRequestDto;
import java.util.List;

public interface CustomerService {

    CustomerProfileResponseDto customerProfile(String username);

    CustomerProfileResponseDto updateCustomerProfile(String username, CustomerProfileUpdateRequestDto request);

    List<CustomerAddressResponseDto> customerAddresses(String username);

    CustomerAddressResponseDto createCustomerAddress(String username, CustomerAddressCreateRequestDto request);

    CustomerAddressResponseDto updateCustomerAddress(String username, Long id, CustomerAddressUpdateRequestDto request);

    void deleteCustomerAddress(String username, Long id);
}
