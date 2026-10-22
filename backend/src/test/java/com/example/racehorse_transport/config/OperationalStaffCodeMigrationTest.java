package com.example.racehorse_transport.config;

import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.repository.StaffRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationalStaffCodeMigrationTest {

    private final StaffRepository staffRepository = mock(StaffRepository.class);

    @Test
    void runNormalizesLegacyAdminDriverAndEscortCodes() {
        Staff admin = staff(1, "ADMIN", "AD-01");
        Staff admin2 = staff(33, "ADMIN", "ADM-02");
        Staff driver = staff(5, "DRIVER", "TX-01");
        Staff escort = staff(8, "ESCORT", "NV-01");
        Staff escort2 = staff(34, "ESCORT", "HT-04");
        Staff manager = staff(2, "MANAGER", "MG-01");
        when(staffRepository.findAll()).thenReturn(List.of(admin, admin2, driver, escort, escort2, manager));

        new OperationalStaffCodeMigration(staffRepository).run();

        assertThat(admin.getStaffCode()).isEqualTo("ADMIN-01");
        assertThat(admin2.getStaffCode()).isEqualTo("ADMIN-02");
        assertThat(driver.getStaffCode()).isEqualTo("DR-01");
        assertThat(escort.getStaffCode()).isEqualTo("ES-01");
        assertThat(escort2.getStaffCode()).isEqualTo("ES-04");
        assertThat(manager.getStaffCode()).isEqualTo("MG-01");
        verify(staffRepository).saveAll(List.of(admin, admin2, driver, escort, escort2));
    }

    @Test
    void runAssignsUnusedCodeWhenTargetCodeAlreadyExists() {
        Staff legacyDriver = staff(5, "DRIVER", "TX-01");
        Staff existingDriver = staff(6, "DRIVER", "DR-01");
        when(staffRepository.findAll()).thenReturn(List.of(legacyDriver, existingDriver));

        new OperationalStaffCodeMigration(staffRepository).run();

        assertThat(legacyDriver.getStaffCode()).isEqualTo("DR-02");
        assertThat(existingDriver.getStaffCode()).isEqualTo("DR-01");
        verify(staffRepository).saveAll(List.of(legacyDriver));
    }

    @Test
    void runDoesNotSaveWhenAllCodesAreAlreadyNormalized() {
        when(staffRepository.findAll()).thenReturn(List.of(
                staff(1, "ADMIN", "ADMIN-01"),
                staff(5, "DRIVER", "DR-01"),
                staff(8, "ESCORT", "ES-01")));

        new OperationalStaffCodeMigration(staffRepository).run();

        verify(staffRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    private Staff staff(int id, String role, String staffCode) {
        Staff staff = new Staff();
        staff.setId(id);
        staff.setRole(role);
        staff.setStaffCode(staffCode);
        return staff;
    }
}
