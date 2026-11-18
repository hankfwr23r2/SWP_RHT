package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.BookingDetailsPayloadDto;
import com.example.racehorse_transport.dto.coordinator.VehicleTripDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Notification;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.repository.NotificationRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.service.StaffNotificationService;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// Thông báo chuông cho nhân viên nội bộ khi có việc tới lượt họ.
// Quản lý nhận theo vai trò; Chuyên viên, Điều phối viên, Tài xế, Hộ tống nhận đúng người được giao đơn/chuyến
// (không tìm ra người được giao thì gửi cả vai trò để việc không bị bỏ sót).
@Service
@RequiredArgsConstructor
public class StaffNotificationServiceImpl implements StaffNotificationService {

    private final NotificationRepository notifications;
    private final StaffRepository staff;

    public void toManagers(Booking booking, String title, String message, String link) {
        save("manager", null, booking, title, message, link);
    }

    public void toAssignedSpecialist(Booking booking, BookingDetailsPayloadDto payload, String title, String message, String link) {
        save("specialist", username("SPECIALIST", intakeId(payload, "specialist")).orElse(null), booking, title, message, link);
    }

    public void toAssignedCoordinator(Booking booking, BookingDetailsPayloadDto payload, String title, String message, String link) {
        save("coordinator", username("COORDINATOR", intakeId(payload, "coordinator")).orElse(null), booking, title, message, link);
    }

    // Tài xế và Hộ tống của mọi chuyến trong đơn
    public void toCrew(Booking booking, BookingDetailsPayloadDto payload, String title, String message) {
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

    // Đơn bị hủy: báo Tài xế, Hộ tống của mọi chuyến và Điều phối viên đã nhận đơn để không ai tiếp tục chạy chuyến.
    // Chưa giao Điều phối viên thì không báo cả vai trò (Quản lý đã được báo riêng ở nơi hủy).
    public void bookingCancelled(Booking booking, BookingDetailsPayloadDto payload, String reason) {
        String title = "Đơn đã hủy · " + booking.getCode();
        String message = "Đơn " + booking.getCode() + " đã bị hủy"
                + (reason == null || reason.isBlank() ? "" : " (" + reason.trim() + ")")
                + ". Dừng mọi công việc của chuyến này.";
        toCrew(booking, payload, title, message);
        username("COORDINATOR", intakeId(payload, "coordinator"))
                .ifPresent(coordinator -> save("coordinator", coordinator, booking, title, message, "/coordinator"));
    }

    // Hủy chuyến khi ngựa đã trên xe: kíp xe trả ngựa và hồ sơ gốc tại điểm đón, lập biên bản trả ngựa
    public void horsesToReturn(Booking booking, BookingDetailsPayloadDto payload, String reason) {
        String title = "Hủy chuyến, trả ngựa · " + booking.getCode();
        String message = "Đơn " + booking.getCode() + " đã bị hủy"
                + (reason == null || reason.isBlank() ? "" : " (" + reason.trim() + ")")
                + ". Không xuất phát. Trả ngựa và toàn bộ hồ sơ gốc cho người giao tại điểm đón, chụp biên bản trả ngựa có chữ ký trên app.";
        toCrew(booking, payload, title, message);
        username("COORDINATOR", intakeId(payload, "coordinator"))
                .ifPresent(coordinator -> save("coordinator", coordinator, booking, title, message, "/coordinator"));
    }

    // Báo cho chủ đơn (khách) khi nhân viên thay đổi đơn của họ, ví dụ nhà xe hủy chuyến
    public void toCustomer(Booking booking, String title, String message) {
        if (booking == null || booking.getCustomerID() == null || booking.getCustomerID().getUser() == null) return;
        save("customer", booking.getCustomerID().getUser().getUsername(), booking, title, message, "/orders/" + booking.getCode());
    }

    private void save(String role, String username, Booking booking, String title, String message, String link) {
        String code = booking != null && booking.getId() != null ? booking.getCode() : null;
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

    private static String intakeId(BookingDetailsPayloadDto payload, String key) {
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
