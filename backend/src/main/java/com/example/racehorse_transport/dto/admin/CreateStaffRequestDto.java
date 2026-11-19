package com.example.racehorse_transport.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CreateStaffRequest")
public class CreateStaffRequestDto {

    @NotBlank(message = "Họ tên không được để trống")
    private String name;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    private String email;

    private String phone;

    @NotBlank(message = "Vai trò không được để trống")
    private String role; // "manager", "specialist", "coordinator", "driver", "escort", "admin"

    public User toUser(String email, String passwordHash) {
        User user = new User();
        user.setUsername(getName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        return user;
    }

    public Staff toStaff(User user, String role, String staffCode, LocalDate hireDate) {
        Staff staff = new Staff();
        staff.setUser(user);
        staff.setRole(role.toUpperCase());
        staff.setStaffCode(staffCode);
        staff.setHireDate(hireDate);
        staff.setEmploymentStatus("ACTIVE");
        staff.setPhone(getPhone() != null ? getPhone().trim() : null);
        return staff;
    }
}
