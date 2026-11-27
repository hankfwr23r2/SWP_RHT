package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.auth.AuthResponseDto;
import com.example.racehorse_transport.dto.auth.ChangePasswordRequestDto;
import com.example.racehorse_transport.dto.auth.EmailVerificationRequestDto;
import com.example.racehorse_transport.dto.auth.LoginRequestDto;
import com.example.racehorse_transport.dto.auth.PasswordResetOtpRequestDto;
import com.example.racehorse_transport.dto.auth.RegisterRequestDto;
import com.example.racehorse_transport.dto.auth.ResetPasswordWithOtpRequestDto;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.exception.InvalidTokenException;
import com.example.racehorse_transport.security.ClientApp;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.security.TurnstileVerifier;
import com.example.racehorse_transport.service.AuthService;
import com.example.racehorse_transport.service.CustomerRegistrationService;
import com.example.racehorse_transport.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// Đường dẫn giữ như hệ thống cũ để màn hình đăng nhập hiện có vẫn dùng được.
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CustomerRegistrationService registrationService;
    private final PasswordResetService passwordResetService;
    private final JwtUtils jwtUtils;
    private final TurnstileVerifier turnstileVerifier;

    @PostMapping("/register")
    public ResponseEntity<ApiResponseDto<Void>> register(
            @RequestHeader(value = "X-Captcha-Token", required = false) String captchaToken,
            @Valid @RequestBody RegisterRequestDto request,
            HttpServletRequest httpRequest) {
        turnstileVerifier.verify(captchaToken, "register");
        registrationService.register(request, httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(null, "Đăng ký thành công. Hãy nhập mã OTP đã gửi về email."));
    }

    @PostMapping("/email-verification/resend")
    public ResponseEntity<ApiResponseDto<Void>> resendEmailOtp(
            @RequestHeader(value = "X-Captcha-Token", required = false) String captchaToken,
            @Valid @RequestBody EmailVerificationRequestDto request) {
        turnstileVerifier.verify(captchaToken, "email_verification");
        authService.resendEmailOtp(request.getEmail());
        return ResponseEntity.ok(ApiResponseDto.success(null, "Nếu email có tài khoản, mã OTP mới sẽ được gửi đến hộp thư của bạn."));
    }

    // Nhập đúng OTP thì đăng nhập luôn
    @PostMapping("/email-verification/confirm")
    public ResponseEntity<ApiResponseDto<AuthResponseDto>> confirmEmail(
            @RequestHeader(value = "X-Captcha-Token", required = false) String captchaToken,
            @Valid @RequestBody EmailVerificationRequestDto request) {
        turnstileVerifier.verify(captchaToken, "email_verification");
        return withRefreshCookie(authService.confirmEmail(request.getEmail(), request.getOtp()), ClientApp.CUSTOMER, "Xác thực email thành công");
    }

    @PostMapping("/customer/login")
    public ResponseEntity<ApiResponseDto<AuthResponseDto>> loginCustomer(
            @RequestHeader(value = "X-Captcha-Token", required = false) String captchaToken,
            @Valid @RequestBody LoginRequestDto request) {
        turnstileVerifier.verify(captchaToken, "customer_login");
        AuthResponseDto response = authService.loginCustomer(request);
        if (response.isEmailVerificationRequired()) {
            return ResponseEntity.ok(ApiResponseDto.success(response, "Hãy nhập mã OTP đã gửi về email để xác thực tài khoản."));
        }
        return withRefreshCookie(response, ClientApp.CUSTOMER, "Đăng nhập Khách hàng thành công");
    }

    @PostMapping("/staff/login")
    public ResponseEntity<ApiResponseDto<AuthResponseDto>> loginStaff(
            @RequestHeader(value = "X-Captcha-Token", required = false) String captchaToken,
            @Valid @RequestBody LoginRequestDto request) {
        turnstileVerifier.verify(captchaToken, "staff_login");
        return withRefreshCookie(authService.loginStaff(request), ClientApp.BACKOFFICE, "Đăng nhập Nội bộ thành công");
    }

    // Làm mới Access Token từ refresh token trong cookie HttpOnly của ứng dụng gửi yêu cầu (header X-Client-App)
    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponseDto<AuthResponseDto>> refreshToken(
            HttpServletRequest request,
            @RequestHeader(value = ClientApp.HEADER, required = false) String clientApp) {
        ClientApp app = ClientApp.fromHeader(clientApp)
                .orElseThrow(() -> new InvalidTokenException("Thiếu hoặc sai header " + ClientApp.HEADER + "."));
        String refreshToken = jwtUtils.getRefreshTokenFromCookies(request, app);
        return ResponseEntity.ok(ApiResponseDto.success(authService.refreshToken(refreshToken, app), "Làm mới Access Token thành công"));
    }

    // Thu hồi token và xóa cookie của ứng dụng gửi yêu cầu; không rõ ứng dụng thì xóa cả hai
    @PostMapping("/logout")
    public ResponseEntity<ApiResponseDto<Void>> logout(
            HttpServletRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestHeader(value = ClientApp.HEADER, required = false) String clientApp) {
        List<ClientApp> apps = ClientApp.fromHeader(clientApp).map(List::of).orElse(List.of(ClientApp.values()));
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        for (ClientApp app : apps) {
            jwtUtils.blacklistToken(jwtUtils.getRefreshTokenFromCookies(request, app));
            response.header(HttpHeaders.SET_COOKIE, jwtUtils.getCleanRefreshCookie(app).toString());
        }
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwtUtils.blacklistToken(authHeader.substring(7));
        }
        return response.body(ApiResponseDto.success(null, "Đăng xuất thành công"));
    }

    // Người đổi mật khẩu lấy từ Authentication, không lấy từ body
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponseDto<Void>> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequestDto request) {
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập để đổi mật khẩu!");
        }
        authService.changePassword(authentication.getName(), request);
        return ResponseEntity.ok(ApiResponseDto.success(null, "Đổi mật khẩu thành công!"));
    }

    @PostMapping("/password-reset/request-otp")
    public ResponseEntity<ApiResponseDto<Void>> requestPasswordResetOtp(
            @RequestHeader(value = "X-Captcha-Token", required = false) String captchaToken,
            @Valid @RequestBody PasswordResetOtpRequestDto request) {
        turnstileVerifier.verify(captchaToken, "password_reset");
        passwordResetService.requestOtp(request.getEmail());
        return ResponseEntity.ok(ApiResponseDto.success(null, "Nếu email có tài khoản, mã OTP sẽ được gửi đến hộp thư của bạn."));
    }

    @PostMapping("/password-reset/complete")
    public ResponseEntity<ApiResponseDto<Void>> resetPasswordWithOtp(
            @RequestHeader(value = "X-Captcha-Token", required = false) String captchaToken,
            @Valid @RequestBody ResetPasswordWithOtpRequestDto request) {
        turnstileVerifier.verify(captchaToken, "password_reset");
        passwordResetService.resetPassword(request);
        return ResponseEntity.ok(ApiResponseDto.success(null, "Đặt lại mật khẩu thành công. Bạn có thể đăng nhập."));
    }

    private ResponseEntity<ApiResponseDto<AuthResponseDto>> withRefreshCookie(AuthResponseDto response, ClientApp app, String message) {
        ResponseCookie refreshCookie = jwtUtils.generateRefreshCookie(response.getUsername(), app);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponseDto.success(response, message));
    }
}
