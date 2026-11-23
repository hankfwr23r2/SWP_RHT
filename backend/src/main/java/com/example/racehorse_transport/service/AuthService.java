package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.AuthResponseDto;
import com.example.racehorse_transport.dto.LoginRequestDto;
import com.example.racehorse_transport.dto.RegisterRequestDto;

public interface AuthService {

    boolean isInactiveStaff(String username);
    
    // Đăng ký tài khoản người dùng mới
    void register(RegisterRequestDto request);

    // 1. Đăng nhập cho Khách hàng
    AuthResponseDto loginCustomer(LoginRequestDto request);

    // Gửi lại OTP xác thực email cho khách
    void resendEmailOtp(String email);

    // Nhập đúng OTP email thì đăng nhập luôn
    AuthResponseDto confirmEmail(String email, String otp);

    // 2. Đăng nhập cho Nhân viên nội bộ
    AuthResponseDto loginStaff(LoginRequestDto request);

    // 3. Làm mới Access Token từ Refresh Token
    AuthResponseDto refreshToken(String refreshToken);

    // 4. Đổi mật khẩu cá nhân cho người dùng đang đăng nhập
    void changePassword(String username, com.example.racehorse_transport.dto.ChangePasswordRequestDto request);
}
