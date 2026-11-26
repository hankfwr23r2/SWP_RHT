package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.auth.RegisterRequestDto;

// Khách tự đăng ký tài khoản.
public interface CustomerRegistrationService {

    // Tạo app_user, user_role CUSTOMER, party, party_role CUSTOMER và terms_acceptance trong một transaction, rồi gửi OTP
    void register(RegisterRequestDto request, String ipAddress);
}
