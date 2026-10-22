package com.example.racehorse_transport.config;

import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.repository.StaffRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpecialistStaffCodeMigrationTest {

    private final StaffRepository staffRepository = mock(StaffRepository.class);

    @Test
    void run_convertsLegacySpecialistCode() {
        Staff specialist = staff(2, "SPECIALIST", "KD-02");
        when(staffRepository.findAll()).thenReturn(List.of(specialist));

        new SpecialistStaffCodeMigration(staffRepository).run();

        assertThat(specialist.getStaffCode()).isEqualTo("SP-02");
        verify(staffRepository).save(specialist);
    }

    @Test
    void run_doesNotChangeOtherRolesWithLegacyCode() {
        Staff coordinator = staff(2, "COORDINATOR", "KD-02");
        when(staffRepository.findAll()).thenReturn(List.of(coordinator));

        new SpecialistStaffCodeMigration(staffRepository).run();

        assertThat(coordinator.getStaffCode()).isEqualTo("KD-02");
        verify(staffRepository, never()).save(coordinator);
    }

    @Test
    void run_failsWhenTargetCodeIsAlreadyInUse() {
        Staff specialist = staff(2, "SPECIALIST", "KD-02");
        Staff anotherStaff = staff(3, "MANAGER", "SP-02");
        when(staffRepository.findAll()).thenReturn(List.of(specialist, anotherStaff));

        assertThatThrownBy(() -> new SpecialistStaffCodeMigration(staffRepository).run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SP-02 is already in use");

        assertThat(specialist.getStaffCode()).isEqualTo("KD-02");
        verify(staffRepository, never()).save(specialist);
    }

    private Staff staff(int id, String role, String staffCode) {
        Staff staff = new Staff();
        staff.setId(id);
        staff.setRole(role);
        staff.setStaffCode(staffCode);
        return staff;
    }
}
