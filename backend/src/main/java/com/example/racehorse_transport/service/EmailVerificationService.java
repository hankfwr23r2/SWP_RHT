package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.AppUser;

// Mã OTP xác thực email của khách: gửi khi đăng ký, hoặc khi tài khoản chưa xác thực đăng nhập.
public interface EmailVerificationService {

    // Gửi mã nếu email có tài khoản và đã qua khoảng chờ gửi lại. Email không có tài khoản thì im lặng bỏ qua.
    void sendOtp(String email);

    // Đúng mã thì đánh dấu email đã xác thực và trả về tài khoản; sai hoặc hết hạn thì ném IllegalArgumentException.
    AppUser confirm(String email, String code);

    // Xóa mọi mã OTP đã hết hạn, cả xác thực email lẫn quên mật khẩu (scheduler gọi định kỳ)
    void deleteExpiredOtps();
}
