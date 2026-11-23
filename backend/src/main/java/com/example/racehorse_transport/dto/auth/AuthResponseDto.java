package com.example.racehorse_transport.dto.auth;

import com.example.racehorse_transport.entity.AppUser;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(name = "AuthResponse")
public class AuthResponseDto {

    private String accessToken;
    private String tokenType;
    private Long userId;
    private String username;
    private String email;
    // Vai trò chính, giữ để tương thích giao diện cũ. Danh sách đầy đủ ở roles.
    private String role;
    private List<String> roles;
    private String fullName;
    private String staffCode;
    // Khách chưa xác thực email: không có token, đã gửi OTP về email
    private boolean emailVerificationRequired;

    // Đăng nhập thành công. roles đã sắp theo thứ tự ưu tiên, phần tử đầu là vai trò chính.
    public static AuthResponseDto from(AppUser user, String accessToken, List<String> roles, String fullName, String staffCode) {
        return AuthResponseDto.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(roles.isEmpty() ? null : roles.get(0))
                .roles(roles)
                .fullName(fullName)
                .staffCode(staffCode)
                .build();
    }

    // Email chưa xác thực: không cấp token
    public static AuthResponseDto verificationRequired(AppUser user, List<String> roles) {
        return AuthResponseDto.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(roles.isEmpty() ? null : roles.get(0))
                .roles(roles)
                .emailVerificationRequired(true)
                .build();
    }
}
