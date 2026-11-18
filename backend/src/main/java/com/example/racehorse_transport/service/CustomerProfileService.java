package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.customer.CustomerProfileResponseDto;
import com.example.racehorse_transport.dto.customer.UpdateCustomerProfileRequestDto;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.User;
import java.util.Optional;

public interface CustomerProfileService {
    CustomerProfileResponseDto getProfile(String username);
    CustomerProfileResponseDto updateProfile(String username, UpdateCustomerProfileRequestDto request);

    // Lưu ngôn ngữ khách chọn trên giao diện ("vi" hoặc "en") để mail gửi đúng ngôn ngữ
    void updateLanguage(String username, String language);

    Optional<Customer> findById(Integer userId);

    boolean existsById(Integer userId);

    Customer getOrCreate(User user, String name, String phone);

    Customer create(User user, String name, String phone, String address);
}
