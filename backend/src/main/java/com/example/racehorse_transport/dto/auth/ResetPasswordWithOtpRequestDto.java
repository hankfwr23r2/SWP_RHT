package com.example.racehorse_transport.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(name = "ResetPasswordWithOtpRequest")
public class ResetPasswordWithOtpRequestDto {

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    private String email;

    @NotBlank(message = "Vui lòng nhập mã OTP")
    @Pattern(regexp = "^\\d{6}$", message = "Mã OTP gồm 6 chữ số")
    private String otp;

    @NotBlank(message = "Vui lòng nhập mật khẩu mới")
    @Size(min = 6, max = 100, message = "Mật khẩu mới phải có ít nhất 6 ký tự")
    private String newPassword;

    @NotBlank(message = "Vui lòng xác nhận mật khẩu mới")
    private String confirmPassword;
}
