package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.ResetPasswordWithOtpRequestDto;

public interface PasswordResetService {
    void requestOtp(String email);
    void resetPassword(ResetPasswordWithOtpRequestDto request);
}
