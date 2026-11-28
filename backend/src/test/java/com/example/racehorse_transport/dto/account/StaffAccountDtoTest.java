package com.example.racehorse_transport.dto.account;

import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Role;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.EmploymentStatus;
import jakarta.validation.Validation;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class StaffAccountDtoTest {

    @Test
    void validatesStaffCodeForCreateAndUpdate() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            String valid = "\t drv._-01 \u2003";
            assertThat(validator.validateValue(StaffAccountCreateRequestDto.class, "staffCode", valid)).isEmpty();
            assertThat(validator.validateValue(StaffAccountUpdateRequestDto.class, "staffCode", valid)).isEmpty();

            for (String invalid : new String[]{"DRV 01", "ĐRV-01", "DRV/01"}) {
                assertThat(validator.validateValue(StaffAccountCreateRequestDto.class, "staffCode", invalid)).isNotEmpty();
                assertThat(validator.validateValue(StaffAccountUpdateRequestDto.class, "staffCode", invalid)).isNotEmpty();
            }
        }
    }

    @Test
    void mapsCreateUpdateAndResponseWithoutAssigningServerFields() {
        var date = LocalDate.of(2026, 11, 23);
        var request = new StaffAccountCreateRequestDto("driver", "DRIVER@example.com", "test-password", "DRIVER",
                " drv-test ", " Driver ", "0123", date, 5L, null, null);
        var user = request.toAccount();
        var hub = new Location();
        hub.setId(5L);
        var staff = request.toEntity(user, hub);
        assertThat(user.getId()).isNull();
        assertThat(user.getStatus()).isNull();
        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.getRole()).isNull();
        assertThat(staff.getUserId()).isNull();
        assertThat(staff.getEmploymentStatus()).isNull();
        assertThat(staff.getStaffCode()).isEqualTo("DRV-TEST");

        var role = new Role();
        role.setCode("DRIVER");
        user.setRole(role);
        user.setStatus(AppUserStatus.LOCKED);
        staff.setUserId(9L);
        staff.setEmploymentStatus(EmploymentStatus.ON_LEAVE);
        new StaffAccountUpdateRequestDto("DRV-NEW", "Updated driver", "0456", date, 5L, "C", date, null)
                .applyTo(staff, hub);

        assertThat(StaffAccountResponseDto.from(staff)).isEqualTo(new StaffAccountResponseDto(
                9L, "driver", "driver@example.com", "DRIVER", AppUserStatus.LOCKED, "DRV-NEW", "Updated driver",
                "0456", date, EmploymentStatus.ON_LEAVE, 5L, "C", date));
    }
}
