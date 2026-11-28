package com.example.racehorse_transport.dto.account;

import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.enums.EmploymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Locale;

public record StaffAccountUpdateRequestDto(
        @NotBlank @Size(max = 30) @Pattern(regexp = "\\p{javaWhitespace}*[A-Za-z0-9._-]+\\p{javaWhitespace}*")
        String staffCode,
        @NotBlank @Size(max = 200) String fullName,
        @NotBlank @Size(max = 20) String phone,
        @NotNull LocalDate hireDate,
        @NotNull @Positive Long homeHubLocationId,
        @Size(max = 10) String driverLicenseClass,
        LocalDate driverLicenseExpiryDate,
        EmploymentStatus employmentStatus) {

    public void applyTo(Staff staff, Location hub) {
        staff.setStaffCode(staffCode.strip().toUpperCase(Locale.ROOT));
        staff.setFullName(fullName.strip());
        staff.setPhone(phone.strip());
        staff.setHireDate(hireDate);
        staff.setHomeHubLocation(hub);
        staff.setDriverLicenseClass(driverLicenseClass);
        staff.setDriverLicenseExpiryDate(driverLicenseExpiryDate);
    }
}
