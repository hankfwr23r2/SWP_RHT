package com.example.racehorse_transport.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AccountResponse")
public class AccountResponseDto {

    private String id;           // Ví dụ "TK-001"
    private Integer userId;      // User ID trong database
    private String name;         // Họ tên
    private String email;        // Email đăng nhập
    private String phone;        // Số điện thoại
    private String role;         // "admin", "manager", "specialist", "coordinator", "driver", "escort", "customer"
    private String status;       // "active" hoặc "locked"
    private String staffCode;    // Mã nhân viên ví dụ "ADMIN-01", "DR-01"
    private LocalDate hireDate;  // Ngày tuyển
    private String tempPassword; // Mật khẩu tạm (chỉ hiển thị 1 lần duy nhất khi vừa tạo)
    private java.util.List<AccountLogResponseDto> history; // Lịch sử audit log của tài khoản
}
