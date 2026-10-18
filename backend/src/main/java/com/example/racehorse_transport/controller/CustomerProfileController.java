package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.customer.CustomerProfileResponse;
import com.example.racehorse_transport.dto.customer.UpdateCustomerProfileRequest;
import com.example.racehorse_transport.response.ApiResponse;
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
    public ResponseEntity<ApiResponse<CustomerProfileResponse>> getProfile(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        String username = extractUsername(authHeader);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Vui lòng đăng nhập để xem thông tin hồ sơ!"));
        }
        CustomerProfileResponse profile = profileService.getProfile(username);
        return ResponseEntity.ok(ApiResponse.success(profile, "Lấy thông tin hồ sơ thành công"));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<CustomerProfileResponse>> updateProfile(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @Valid @RequestBody UpdateCustomerProfileRequest request) {
        String username = extractUsername(authHeader);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Vui lòng đăng nhập để cập nhật hồ sơ!"));
        }
        CustomerProfileResponse updated = profileService.updateProfile(username, request);
        return ResponseEntity.ok(ApiResponse.success(updated, "Cập nhật hồ sơ thành công"));
    }
}
