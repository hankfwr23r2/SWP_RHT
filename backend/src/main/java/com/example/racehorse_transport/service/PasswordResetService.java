package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.ResetPasswordWithOtpRequest;

public interface PasswordResetService {
    void requestOtp(String email);
    void resetPassword(ResetPasswordWithOtpRequest request);
}
