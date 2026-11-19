package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.AuthResponseDto;
import com.example.racehorse_transport.dto.ChangePasswordRequestDto;
import com.example.racehorse_transport.dto.EmailVerificationRequestDto;
import com.example.racehorse_transport.dto.LoginRequestDto;
import com.example.racehorse_transport.dto.PasswordResetOtpRequestDto;
import com.example.racehorse_transport.dto.RegisterRequestDto;
import com.example.racehorse_transport.dto.ResetPasswordWithOtpRequestDto;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.security.TurnstileVerifier;
import com.example.racehorse_transport.service.AuthService;
import com.example.racehorse_transport.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final JwtUtils jwtUtils;
    private final TurnstileVerifier turnstileVerifier;

    @PostMapping("/password-reset/request-otp")
    public ResponseEntity<ApiResponseDto<Void>> requestPasswordResetOtp(
            @RequestHeader(value = "X-Captcha-Token", required = false) String captchaToken,
            @Valid @RequestBody PasswordResetOtpRequestDto request) {
        turnstileVerifier.verify(captchaToken, "password_reset");
        passwordResetService.requestOtp(request.getEmail());
        return ResponseEntity.ok(ApiResponseDto.success(null,
                "Nếu email có tài khoản, mã OTP sẽ được gửi đến hộp thư của bạn."));
    }

    @PostMapping("/password-reset/complete")
    public ResponseEntity<ApiResponseDto<Void>> resetPasswordWithOtp(
            @RequestHeader(value = "X-Captcha-Token", required = false) String captchaToken,
            @Valid @RequestBody ResetPasswordWithOtpRequestDto request) {
        turnstileVerifier.verify(captchaToken, "password_reset");
        passwordResetService.resetPassword(request);
        return ResponseEntity.ok(ApiResponseDto.success(null, "Đặt lại mật khẩu thành công. Bạn có thể đăng nhập."));
    }

    // API Đăng ký tài khoản
    @PostMapping("/register")
    public ResponseEntity<ApiResponseDto<String>> register(
            @RequestHeader(value = "X-Captcha-Token", required = false) String captchaToken,
            @Valid @RequestBody RegisterRequestDto request) {
        turnstileVerifier.verify(captchaToken, "register");
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success("Đăng ký tài khoản thành công!", "Đăng ký thành công. Hãy nhập mã OTP đã gửi về email."));
    }

    // API Gửi lại OTP xác thực email
    @PostMapping("/email-verification/resend")
    public ResponseEntity<ApiResponseDto<Void>> resendEmailOtp(
            @RequestHeader(value = "X-Captcha-Token", required = false) String captchaToken,
            @Valid @RequestBody EmailVerificationRequestDto request) {
        turnstileVerifier.verify(captchaToken, "email_verification");
        authService.resendEmailOtp(request.getEmail());
        return ResponseEntity.ok(ApiResponseDto.success(null, "Nếu email có tài khoản, mã OTP mới sẽ được gửi đến hộp thư của bạn."));
    }

    // API Nhập OTP xác thực email, đúng thì đăng nhập luôn
    @PostMapping("/email-verification/confirm")
    public ResponseEntity<ApiResponseDto<AuthResponseDto>> confirmEmail(
            @RequestHeader(value = "X-Captcha-Token", required = false) String captchaToken,
            @Valid @RequestBody EmailVerificationRequestDto request) {
        turnstileVerifier.verify(captchaToken, "email_verification");
        return withRefreshCookie(authService.confirmEmail(request.getEmail(), request.getOtp()), "Xác thực email thành công");
    }

    // API Đăng nhập Khách hàng
    @PostMapping("/customer/login")
    public ResponseEntity<ApiResponseDto<AuthResponseDto>> loginCustomer(
            @RequestHeader(value = "X-Captcha-Token", required = false) String captchaToken,
            @Valid @RequestBody LoginRequestDto request) {
        turnstileVerifier.verify(captchaToken, "customer_login");
        AuthResponseDto authResponse = authService.loginCustomer(request);
        if (authResponse.isEmailVerificationRequired()) {
            return ResponseEntity.ok(ApiResponseDto.success(authResponse, "Hãy nhập mã OTP đã gửi về email để xác thực tài khoản."));
        }
        return withRefreshCookie(authResponse, "Đăng nhập Khách hàng thành công");
    }

    private ResponseEntity<ApiResponseDto<AuthResponseDto>> withRefreshCookie(AuthResponseDto authResponse, String message) {
        ResponseCookie refreshCookie = jwtUtils.generateRefreshCookie(authResponse.getUsername());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponseDto.success(authResponse, message));
    }

    // API Đăng nhập Nhân viên nội bộ
    @PostMapping("/staff/login")
    public ResponseEntity<ApiResponseDto<AuthResponseDto>> loginStaff(
            @RequestHeader(value = "X-Captcha-Token", required = false) String captchaToken,
            @Valid @RequestBody LoginRequestDto request) {
        turnstileVerifier.verify(captchaToken, "staff_login");
        AuthResponseDto authResponse = authService.loginStaff(request);
        ResponseCookie refreshCookie = jwtUtils.generateRefreshCookie(authResponse.getUsername());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponseDto.success(authResponse, "Đăng nhập Nội bộ thành công"));
    }

    // API Làm mới Access Token từ HttpOnly Cookie
    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponseDto<AuthResponseDto>> refreshToken(HttpServletRequest request) {
        String refreshToken = jwtUtils.getRefreshTokenFromCookies(request);
        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponseDto.error("Không tìm thấy Refresh Token trong cookie!"));
        }
        AuthResponseDto authResponse = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(ApiResponseDto.success(authResponse, "Làm mới Access Token thành công"));
    }

    // API Đăng xuất - Thu hồi token và Xóa HttpOnly Cookie
    @PostMapping("/logout")
    public ResponseEntity<ApiResponseDto<String>> logout(
            HttpServletRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {

        // 1. Thu hồi Refresh Token từ cookie
        String refreshToken = jwtUtils.getRefreshTokenFromCookies(request);
        if (refreshToken != null) {
            jwtUtils.blacklistToken(refreshToken);
        }

        // 2. Thu hồi Access Token từ Authorization header nếu có
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwtUtils.blacklistToken(authHeader.substring(7));
        }

        ResponseCookie cleanCookie = jwtUtils.getCleanRefreshCookie();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cleanCookie.toString())
                .body(ApiResponseDto.success("Đăng xuất thành công, token đã bị thu hồi", "Thành công"));
    }

    // API Đổi mật khẩu cá nhân cho người dùng đang đăng nhập
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponseDto<Void>> changePassword(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @Valid @RequestBody ChangePasswordRequestDto request) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponseDto.error("Vui lòng đăng nhập để đổi mật khẩu!"));
        }

        String token = authHeader.substring(7);
        if (!jwtUtils.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponseDto.error("Phiên đăng nhập không hợp lệ hoặc đã hết hạn!"));
        }

        String username = jwtUtils.getUsernameFromToken(token);
        authService.changePassword(username, request);

        return ResponseEntity.ok(ApiResponseDto.success(null, "Đổi mật khẩu thành công!"));
    }
}
