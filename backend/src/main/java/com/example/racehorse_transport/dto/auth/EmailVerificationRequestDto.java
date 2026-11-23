package com.example.racehorse_transport.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(name = "EmailVerificationRequest")
public class EmailVerificationRequestDto {

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    private String email;

    // Chỉ bắt buộc khi xác nhận; khi gửi lại mã thì bỏ trống
    @Pattern(regexp = "^$|^\\d{6}$", message = "Mã OTP gồm 6 chữ số")
    private String otp;
}
