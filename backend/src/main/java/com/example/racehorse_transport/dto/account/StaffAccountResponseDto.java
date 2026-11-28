package com.example.racehorse_transport.dto.account;

import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.EmploymentStatus;
import java.time.LocalDate;

public record StaffAccountResponseDto(Long userId, String username, String email, String roleCode,
                                      AppUserStatus accountStatus, String staffCode, String fullName,
                                      String phone, LocalDate hireDate, EmploymentStatus employmentStatus,
                                      Long homeHubLocationId, String driverLicenseClass,
                                      LocalDate driverLicenseExpiryDate) {

    public static StaffAccountResponseDto from(Staff staff) {
        var user = staff.getUser();
        return new StaffAccountResponseDto(staff.getUserId(), user.getUsername(), user.getEmail(),
                user.getRole().getCode(), user.getStatus(), staff.getStaffCode(), staff.getFullName(), staff.getPhone(),
                staff.getHireDate(), staff.getEmploymentStatus(), staff.getHomeHubLocation().getId(),
                staff.getDriverLicenseClass(), staff.getDriverLicenseExpiryDate());
    }
}
