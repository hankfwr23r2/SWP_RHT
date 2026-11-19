package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.User;

public interface EmailVerificationService {

    void cleanExpiredOtps();

    void sendOtp(String email);

    User confirm(String email, String code);
}
