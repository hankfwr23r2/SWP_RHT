package com.example.racehorse_transport.dto.customer;

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
public class UpdateCustomerProfileRequest {

    @NotBlank(message = "Họ và tên không được để trống")
    @Size(min = 2, max = 100, message = "Họ và tên phải từ 2 đến 100 ký tự")
    @Pattern(regexp = ContactPatterns.PERSON_NAME, message = "Họ tên chỉ được chứa chữ cái, dấu cách, dấu nháy hoặc gạch nối")
    private String fullName;

    @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
    @Pattern(regexp = ContactPatterns.OPTIONAL_REGIONAL_PHONE, message = "Số điện thoại phải thuộc Việt Nam, Campuchia hoặc Lào")
    private String phone;

    @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
    private String address;
}
