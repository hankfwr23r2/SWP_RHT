package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.AuthResponse;
import com.example.racehorse_transport.dto.LoginRequest;
import com.example.racehorse_transport.dto.RegisterRequest;

public interface AuthService {
    
    // Đăng ký tài khoản người dùng mới
    void register(RegisterRequest request);

    // 1. Đăng nhập cho Khách hàng
    AuthResponse loginCustomer(LoginRequest request);

    // 2. Đăng nhập cho Nhân viên nội bộ
    AuthResponse loginStaff(LoginRequest request);

    // 3. Làm mới Access Token từ Refresh Token
    AuthResponse refreshToken(String refreshToken);

    // 4. Đổi mật khẩu cá nhân cho người dùng đang đăng nhập
    void changePassword(String username, com.example.racehorse_transport.dto.ChangePasswordRequest request);
}
