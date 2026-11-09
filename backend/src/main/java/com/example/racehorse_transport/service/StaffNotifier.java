package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.booking.BookingDetailsPayload;
import com.example.racehorse_transport.dto.coordinator.VehicleTripDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Notification;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.repository.NotificationRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

// Thông báo chuông cho nhân viên nội bộ khi có việc tới lượt họ.
// Quản lý nhận theo vai trò; Chuyên viên, Điều phối viên, Tài xế, Hộ tống nhận đúng người được giao đơn/chuyến
// (không tìm ra người được giao thì gửi cả vai trò để việc không bị bỏ sót).
@Service
@RequiredArgsConstructor
public class StaffNotifier {

    private final NotificationRepository notifications;
    private final StaffRepository staff;

    public void toManagers(Booking booking, String title, String message, String link) {
        save("manager", null, booking, title, message, link);
    }

    public void toAssignedSpecialist(Booking booking, BookingDetailsPayload payload, String title, String message, String link) {
        save("specialist", username("SPECIALIST", intakeId(payload, "specialist")).orElse(null), booking, title, message, link);
    }

    public void toAssignedCoordinator(Booking booking, BookingDetailsPayload payload, String title, String message, String link) {
        save("coordinator", username("COORDINATOR", intakeId(payload, "coordinator")).orElse(null), booking, title, message, link);
    }

    // Tài xế và Hộ tống của mọi chuyến trong đơn
    public void toCrew(Booking booking, BookingDetailsPayload payload, String title, String message) {
        if (payload == null || payload.getTrips() == null) return;
        for (VehicleTripDto trip : payload.getTrips()) toTripCrew(booking, trip, title, message);
    }

    public void toTripCrew(Booking booking, VehicleTripDto trip, String title, String message) {
        if (trip == null) return;
        if (trip.getDriverId() != null) {
            save("driver", username("DRIVER", trip.getDriverId()).orElse(null), booking, title, message, "/driver");
        }
        if (trip.getEscortId() != null) {
            save("escort", username("ESCORT", trip.getEscortId()).orElse(null), booking, title, message, "/escort");
        }
    }

    private void save(String role, String username, Booking booking, String title, String message, String link) {
        String code = booking != null && booking.getId() != null ? "BK-" + booking.getId() : null;
        notifications.save(Notification.builder()
                .recipientRole(role)
                .recipientUsername(username)
                .title(title)
                .message(message)
                .link(link)
                .bookingId(code)
                .isRead(false)
                .createdAt(Instant.now())
                .build());
    }

    private static String intakeId(BookingDetailsPayload payload, String key) {
        if (payload == null || !(payload.getIntake() instanceof Map<?, ?> intake)) return null;
        if (!(intake.get(key) instanceof Map<?, ?> person)) return null;
        Object id = person.get("id");
        return id == null ? null : id.toString();
    }

    // Mã giao việc có thể là mã nhân sự (SP-01), mã người dùng hoặc tên đăng nhập
    private Optional<String> username(String role, String assigned) {
        if (assigned == null || assigned.isBlank()) return Optional.empty();
        String wanted = assigned.trim().toUpperCase(Locale.ROOT);
        for (Staff member : staff.findByRoleIgnoreCase(role)) {
            if (member.getUser() == null || member.getUser().getUsername() == null) continue;
            String username = member.getUser().getUsername();
            if (wanted.equals(Objects.toString(member.getStaffCode(), "").toUpperCase(Locale.ROOT))
                    || wanted.equals(Objects.toString(member.getId(), ""))
                    || wanted.equals(Objects.toString(member.getUser().getId(), ""))
                    || wanted.equals(username.toUpperCase(Locale.ROOT))) {
                return Optional.of(username);
            }
        }
        return Optional.empty();
    }
}
