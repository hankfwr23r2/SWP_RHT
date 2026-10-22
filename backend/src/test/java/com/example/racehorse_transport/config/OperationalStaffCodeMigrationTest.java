package com.example.racehorse_transport.config;

import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingAssignment;
import com.example.racehorse_transport.repository.BookingAssignmentRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationalStaffCodeMigrationTest {

    private final StaffRepository staffRepository = mock(StaffRepository.class);
    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final BookingAssignmentRepository bookingAssignmentRepository = mock(BookingAssignmentRepository.class);

    @Test
    void runNormalizesLegacyAdminDriverAndEscortCodes() {
        Staff admin = staff(1, "ADMIN", "AD-01");
        Staff admin2 = staff(33, "ADMIN", "ADM-02");
        Staff driver = staff(5, "DRIVER", "TX-01");
        Staff escort = staff(8, "ESCORT", "NV-01");
        Staff escort2 = staff(34, "ESCORT", "HT-04");
        Staff manager = staff(2, "MANAGER", "MG-01");
        when(staffRepository.findAll()).thenReturn(List.of(admin, admin2, driver, escort, escort2, manager));
        when(bookingRepository.findAll()).thenReturn(List.of());

        migration().run();

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
        when(bookingRepository.findAll()).thenReturn(List.of());

        migration().run();

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
        when(bookingRepository.findAll()).thenReturn(List.of());

        migration().run();

        verify(staffRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void runMigratesTripAssignmentsToTheAssignedStaffCurrentCodes() {
        Staff driver = staff(5, "DRIVER", "DR-02");
        Staff escort = staff(8, "ESCORT", "ES-01");
        Booking booking = new Booking();
        booking.setId(13);
        booking.setDetailsJson("{\"trips\":[{\"tripId\":\"TRP-013-1\",\"driverId\":\"TX-01\",\"escortId\":\"NV-01\"}],\"custom\":true}");
        when(staffRepository.findAll()).thenReturn(List.of(driver, escort));
        when(bookingRepository.findAll()).thenReturn(List.of(booking));
        when(bookingAssignmentRepository.findByBookingID_Id(13)).thenReturn(List.of(
                assignment("DRIVER", "TRP-013-1", driver),
                assignment("ESCORT", "TRP-013-1", escort)));

        migration().run();

        assertThat(booking.getDetailsJson()).contains("\"driverId\":\"DR-02\"")
                .contains("\"escortId\":\"ES-01\"")
                .contains("\"custom\":true");
        verify(bookingRepository).saveAll(List.of(booking));
    }

    private OperationalStaffCodeMigration migration() {
        return new OperationalStaffCodeMigration(staffRepository, bookingRepository, bookingAssignmentRepository,
                new ObjectMapper());
    }

    private BookingAssignment assignment(String role, String tripId, Staff staff) {
        Booking booking = new Booking();
        booking.setId(13);
        BookingAssignment assignment = new BookingAssignment();
        assignment.setBookingID(booking);
        assignment.setStaffID(staff);
        assignment.setAssignRole(role);
        assignment.setNote("assignment for " + tripId);
        return assignment;
    }

    private Staff staff(int id, String role, String staffCode) {
        Staff staff = new Staff();
        staff.setId(id);
        staff.setRole(role);
        staff.setStaffCode(staffCode);
        return staff;
    }
}
