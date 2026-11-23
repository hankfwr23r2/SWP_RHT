package com.example.racehorse_transport.dto.account;

import com.example.racehorse_transport.entity.AppUser;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Staff;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Locale;

public record StaffAccountCreateRequestDto(
        @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "[A-Za-z0-9._-]+") String username,
        @NotBlank @Email @Size(max = 254) String email,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @NotBlank @Size(min = 6, max = 72) String password,
        @NotBlank @Size(max = 30) String roleCode,
        @NotBlank @Size(max = 30) String staffCode,
        @NotBlank @Size(max = 200) String fullName,
        @NotBlank @Size(max = 20) String phone,
        @NotNull LocalDate hireDate,
        @NotNull @Positive Long homeHubLocationId,
        @Size(max = 10) String driverLicenseClass,
        LocalDate driverLicenseExpiryDate) {

    public AppUser toAccount() {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setEmail(email.toLowerCase(Locale.ROOT));
        return user;
    }

    @Override
    public String toString() {
        return "StaffAccountCreateRequestDto";
    }

    public Staff toEntity(AppUser user, Location hub) {
        Staff staff = new Staff();
        staff.setUser(user);
        staff.setStaffCode(staffCode.strip().toUpperCase(Locale.ROOT));
        staff.setFullName(fullName.strip());
        staff.setPhone(phone.strip());
        staff.setHireDate(hireDate);
        staff.setHomeHubLocation(hub);
        staff.setDriverLicenseClass(driverLicenseClass);
        staff.setDriverLicenseExpiryDate(driverLicenseExpiryDate);
        return staff;
    }
}
