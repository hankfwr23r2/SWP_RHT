package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.customer.CustomerProfileResponseDto;
import com.example.racehorse_transport.dto.customer.UpdateCustomerProfileRequestDto;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.service.CustomerProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/customer/profile")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class CustomerProfileController {

    private final CustomerProfileService profileService;
    private final JwtUtils jwtUtils;

    private String extractUsername(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtUtils.validateToken(token)) {
                return jwtUtils.getUsernameFromToken(token);
            }
        }
        return null;
    }

    @GetMapping
    public ResponseEntity<ApiResponseDto<CustomerProfileResponseDto>> getProfile(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        String username = extractUsername(authHeader);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponseDto.error("Vui lòng đăng nhập để xem thông tin hồ sơ!"));
        }
        CustomerProfileResponseDto profile = profileService.getProfile(username);
        return ResponseEntity.ok(ApiResponseDto.success(profile, "Lấy thông tin hồ sơ thành công"));
    }

    @PutMapping
    public ResponseEntity<ApiResponseDto<CustomerProfileResponseDto>> updateProfile(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @Valid @RequestBody UpdateCustomerProfileRequestDto request) {
        String username = extractUsername(authHeader);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponseDto.error("Vui lòng đăng nhập để cập nhật hồ sơ!"));
        }
        CustomerProfileResponseDto updated = profileService.updateProfile(username, request);
        return ResponseEntity.ok(ApiResponseDto.success(updated, "Cập nhật hồ sơ thành công"));
    }

    // Khách đổi VI/EN trên giao diện: lưu lại để mail hóa đơn gửi đúng ngôn ngữ
    @PutMapping("/language")
    public ResponseEntity<ApiResponseDto<String>> updateLanguage(
            org.springframework.security.core.Authentication authentication,
            @RequestBody java.util.Map<String, String> body) {
        String language = body == null ? null : body.get("language");
        profileService.updateLanguage(authentication.getName(), language);
        return ResponseEntity.ok(ApiResponseDto.success(language, "Đã lưu ngôn ngữ"));
    }
}
