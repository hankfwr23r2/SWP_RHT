package com.example.racehorse_transport.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import com.example.racehorse_transport.validation.ContactPatterns;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "Username không được để trống")
    @Size(min = 3, max = 50, message = "Username phải từ 3 đến 50 ký tự")
    private String username;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    @Pattern(regexp = ContactPatterns.EMAIL_WITH_DOMAIN, message = "Email cần có tên miền hợp lệ, ví dụ ten@example.com")
    private String email;

    @NotBlank(message = "Password không được để trống")
    @Size(min = 6, max = 100, message = "Password phải có ít nhất 6 ký tự")
    private String password;

    // Thông tin khách hàng bắt buộc khi đăng ký
    @NotBlank(message = "Họ và tên không được để trống")
    @Size(min = 2, max = 100, message = "Họ và tên phải từ 2 đến 100 ký tự")
    @Pattern(regexp = ContactPatterns.PERSON_NAME, message = "Họ tên chỉ được chứa chữ cái, dấu cách, dấu nháy hoặc gạch nối")
    private String fullName;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = ContactPatterns.REGIONAL_PHONE, message = "Số điện thoại phải thuộc Việt Nam, Campuchia hoặc Lào")
    private String phone;
    private String address;
}
