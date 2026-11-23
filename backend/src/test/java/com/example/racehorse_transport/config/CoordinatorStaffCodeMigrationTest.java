package com.example.racehorse_transport.config;

import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.repository.StaffRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoordinatorStaffCodeMigrationTest {

    private final StaffRepository staffRepository = mock(StaffRepository.class);

    @Test
    void run_convertsLegacyCoordinatorCode() {
        Staff existingCoordinator = staff(2, "COORDINATOR", "DP-02");
        Staff firstCoordinator = staff(1, "COORDINATOR", "CO-01");
        when(staffRepository.findAll()).thenReturn(List.of(existingCoordinator, firstCoordinator));

        new CoordinatorStaffCodeMigration(staffRepository).run();

        assertThat(existingCoordinator.getStaffCode()).isEqualTo("CO-02");
        verify(staffRepository).saveAll(List.of(existingCoordinator));
    }

    @Test
    void run_assignsUnusedCodeWhenLegacyCodeWouldCollide() {
        Staff existingCoordinator = staff(2, "COORDINATOR", "DP-02");
        Staff anotherStaff = staff(3, "SPECIALIST", "CO-02");
        when(staffRepository.findAll()).thenReturn(List.of(existingCoordinator, anotherStaff));

        new CoordinatorStaffCodeMigration(staffRepository).run();

        assertThat(existingCoordinator.getStaffCode()).isEqualTo("CO-03");
        verify(staffRepository).saveAll(List.of(existingCoordinator));
    }

    private Staff staff(int id, String role, String staffCode) {
        Staff staff = new Staff();
        staff.setId(id);
        staff.setRole(role);
        staff.setStaffCode(staffCode);
        return staff;
    }
}
