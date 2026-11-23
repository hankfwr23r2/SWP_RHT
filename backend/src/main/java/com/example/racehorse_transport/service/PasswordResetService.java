package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.auth.ResetPasswordWithOtpRequestDto;

// Quên mật khẩu: gửi mã OTP về email rồi đặt mật khẩu mới bằng mã đó.
public interface PasswordResetService {

    // Không tiết lộ email có tài khoản hay không: luôn trả về bình thường
    void requestOtp(String email);

    void resetPassword(ResetPasswordWithOtpRequestDto request);
}
