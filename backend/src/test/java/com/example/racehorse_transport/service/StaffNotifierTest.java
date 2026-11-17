package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.booking.BookingDetailsPayload;
import com.example.racehorse_transport.dto.coordinator.VehicleTripDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Notification;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.repository.NotificationRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StaffNotifierTest {
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final StaffRepository staff = mock(StaffRepository.class);
    private final StaffNotifier notifier = new StaffNotifier(notifications, staff);

    @Test
    void cancelledBookingReachesEveryTripCrewAndTheAssignedCoordinator() {
        when(staff.findByRoleIgnoreCase("DRIVER")).thenReturn(List.of(member("DR-01", "driver01")));
        when(staff.findByRoleIgnoreCase("ESCORT")).thenReturn(List.of(member("ES-01", "escort01")));
        when(staff.findByRoleIgnoreCase("COORDINATOR")).thenReturn(List.of(member("CO-01", "coordinator01")));
        BookingDetailsPayload payload = new BookingDetailsPayload();
        payload.setTrips(List.of(VehicleTripDto.builder().driverId("DR-01").escortId("ES-01").build()));
        payload.setIntake(Map.of("coordinator", Map.of("id", "CO-01")));

        notifier.bookingCancelled(booking(), payload, "khách hủy");

        ArgumentCaptor<Notification> sent = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, times(3)).save(sent.capture());
        assertThat(sent.getAllValues()).extracting(Notification::getRecipientUsername)
                .containsExactlyInAnyOrder("driver01", "escort01", "coordinator01");
        assertThat(sent.getAllValues()).allSatisfy(notification -> {
            assertThat(notification.getTitle()).isEqualTo("Đơn đã hủy · BK-TEST0001");
            assertThat(notification.getMessage()).contains("khách hủy");
        });
    }

    @Test
    void cancelledBookingWithoutCrewOrCoordinatorDoesNotBroadcastToWholeRoles() {
        notifier.bookingCancelled(booking(), new BookingDetailsPayload(), null);

        verify(notifications, never()).save(any());
    }

    private Booking booking() {
        Booking booking = new Booking();
        booking.setId(1);
        booking.setCode("BK-TEST0001");
        return booking;
    }

    private Staff member(String code, String username) {
        User user = new User();
        user.setUsername(username);
        Staff member = new Staff();
        member.setStaffCode(code);
        member.setUser(user);
        return member;
    }
}
