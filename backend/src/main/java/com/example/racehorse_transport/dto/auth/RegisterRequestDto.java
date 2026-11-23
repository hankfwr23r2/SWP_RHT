package com.example.racehorse_transport.dto.auth;

import com.example.racehorse_transport.entity.AppUser;
import com.example.racehorse_transport.entity.Country;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.enums.CustomerType;
import com.example.racehorse_transport.util.ContactPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(name = "RegisterRequest")
public class RegisterRequestDto {

    @NotBlank(message = "Username không được để trống")
    @Size(min = 3, max = 50, message = "Username phải từ 3 đến 50 ký tự")
    private String username;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    @Pattern(regexp = ContactPatterns.GMAIL, message = "Chỉ nhận địa chỉ Gmail, ví dụ ten@gmail.com")
    private String email;

    @NotBlank(message = "Password không được để trống")
    @Size(min = 6, max = 100, message = "Password phải có ít nhất 6 ký tự")
    private String password;

    @NotBlank(message = "Họ và tên không được để trống")
    @Size(min = 2, max = 100, message = "Họ và tên phải từ 2 đến 100 ký tự")
    @Pattern(regexp = ContactPatterns.PERSON_NAME, message = "Họ tên chỉ được chứa chữ cái, dấu cách, dấu nháy hoặc gạch nối")
    private String fullName;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = ContactPatterns.REGIONAL_PHONE, message = "Số điện thoại phải thuộc Việt Nam, Campuchia hoặc Lào")
    private String phone;

    @Size(max = 500, message = "Địa chỉ tối đa 500 ký tự")
    private String address;

    // Ngôn ngữ đang chọn trên giao diện ("vi" hoặc "en"); giá trị khác thì bỏ qua
    private String language;

    // Phải đồng ý Điều khoản vận chuyển mới đăng ký được; giờ và phiên bản do server ghi
    private Boolean acceptedTerms;

    // Tài khoản mới, chưa lưu. Vai trò, trạng thái và cờ xác thực email do service đặt.
    public AppUser toEntity(String passwordHash) {
        AppUser user = new AppUser();
        user.setUsername(username.trim());
        user.setEmail(email.trim().toLowerCase(Locale.ROOT));
        user.setPasswordHash(passwordHash);
        if ("vi".equals(language) || "en".equals(language)) {
            user.setPreferredLanguage(language);
        }
        return user;
    }

    // Hồ sơ khách mới, chưa lưu. Form đăng ký chỉ dành cho cá nhân.
    public Customer toCustomer(AppUser user, Country country) {
        Customer customer = new Customer();
        customer.setUser(user);
        customer.setCustomerType(CustomerType.PERSON);
        customer.setFullName(fullName.trim());
        customer.setPhone(phone.trim());
        customer.setAddress(address == null || address.isBlank() ? null : address.trim());
        customer.setCountry(country);
        return customer;
    }
}
