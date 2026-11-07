package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.customer.CustomerProfileResponse;
import com.example.racehorse_transport.dto.customer.UpdateCustomerProfileRequest;

public interface CustomerProfileService {
    CustomerProfileResponse getProfile(String username);
    CustomerProfileResponse updateProfile(String username, UpdateCustomerProfileRequest request);

    // Lưu ngôn ngữ khách chọn trên giao diện ("vi" hoặc "en") để mail gửi đúng ngôn ngữ
    void updateLanguage(String username, String language);
}
