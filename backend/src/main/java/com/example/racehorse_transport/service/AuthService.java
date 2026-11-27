package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.auth.AuthResponseDto;
import com.example.racehorse_transport.dto.auth.ChangePasswordRequestDto;
import com.example.racehorse_transport.dto.auth.LoginRequestDto;
import com.example.racehorse_transport.security.ClientApp;

// Đăng nhập, làm mới token, đổi mật khẩu. Vai trò lấy từ app_user.role_code. Đăng ký nằm ở CustomerRegistrationService.
public interface AuthService {

    AuthResponseDto loginCustomer(LoginRequestDto request);

    AuthResponseDto loginStaff(LoginRequestDto request);

    void resendEmailOtp(String email);

    AuthResponseDto confirmEmail(String email, String otp);

    // Refresh token lấy từ cookie của ứng dụng app; sai loại, hết hạn hay không đúng ứng dụng thì ném InvalidTokenException
    AuthResponseDto refreshToken(String refreshToken, ClientApp app);

    void changePassword(String username, ChangePasswordRequestDto request);

    // Tài khoản ACTIVE và, nếu là nhân viên, chưa nghỉ việc. Bộ lọc JWT dùng để từ chối token của tài khoản bị khóa.
    boolean isActive(String username);
}
