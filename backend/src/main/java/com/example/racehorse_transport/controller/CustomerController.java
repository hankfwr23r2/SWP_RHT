package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.customer.CustomerAddressCreateRequestDto;
import com.example.racehorse_transport.dto.customer.CustomerAddressResponseDto;
import com.example.racehorse_transport.dto.customer.CustomerAddressUpdateRequestDto;
import com.example.racehorse_transport.dto.customer.CustomerProfileResponseDto;
import com.example.racehorse_transport.dto.customer.CustomerProfileUpdateRequestDto;
import com.example.racehorse_transport.service.CustomerService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/customers/me")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customers;

    @GetMapping
    public ApiResponseDto<CustomerProfileResponseDto> getCustomerProfile(Authentication actor) {
        return ApiResponseDto.success(customers.customerProfile(actor.getName()), "Hồ sơ khách hàng");
    }

    @PutMapping
    public ApiResponseDto<CustomerProfileResponseDto> updateCustomerProfile(
            Authentication actor, @Valid @RequestBody CustomerProfileUpdateRequestDto request) {
        return ApiResponseDto.success(customers.updateCustomerProfile(actor.getName(), request), "Đã cập nhật hồ sơ");
    }

    @GetMapping("/addresses")
    public ApiResponseDto<List<CustomerAddressResponseDto>> listCustomerAddresses(Authentication actor) {
        return ApiResponseDto.success(customers.customerAddresses(actor.getName()), "Sổ địa chỉ");
    }

    @PostMapping("/addresses")
    public ResponseEntity<ApiResponseDto<CustomerAddressResponseDto>> createCustomerAddress(
            Authentication actor, @Valid @RequestBody CustomerAddressCreateRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(customers.createCustomerAddress(actor.getName(), request), "Đã thêm địa chỉ"));
    }

    @PutMapping("/addresses/{id}")
    public ApiResponseDto<CustomerAddressResponseDto> updateCustomerAddress(
            Authentication actor, @PathVariable Long id, @Valid @RequestBody CustomerAddressUpdateRequestDto request) {
        return ApiResponseDto.success(customers.updateCustomerAddress(actor.getName(), id, request), "Đã cập nhật địa chỉ");
    }

    @DeleteMapping("/addresses/{id}")
    public ApiResponseDto<Void> deleteCustomerAddress(Authentication actor, @PathVariable Long id) {
        customers.deleteCustomerAddress(actor.getName(), id);
        return ApiResponseDto.success(null, "Đã xóa địa chỉ");
    }
}
