package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.booking.BookingDetailsPayload;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Notification;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.*;
import com.example.racehorse_transport.util.BookingDetailsJson;
import com.example.racehorse_transport.util.BookingSchedulePolicy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BookingScheduleService {
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final QuotationRepository quotations;
    private final BookingVehicleRepository vehicles;
    private final BookingAssignmentRepository assignments;
    private final NotificationRepository notifications;
    private final SystemConfigService config;
    private final BillMailService mail;
    private final Clock clock;
    private final ObjectMapper json = new ObjectMapper();

    @Transactional(readOnly = true)
    public List<Integer> candidates() {
        return bookings.findScheduleChangeCandidates(BookingSchedulePolicy.REVIEW_STATUSES,
                LocalDate.now(clock.withZone(BookingSchedulePolicy.VIETNAM))
                        .atStartOfDay(BookingSchedulePolicy.VIETNAM).toInstant());
    }

    // Each order is locked in its own transaction, as are checkout and payment callbacks.
    @Transactional
    public void flagOverdue(Integer id) {
        Booking booking = bookings.findByIdForUpdate(id).orElse(null);
        if (booking == null || !BookingSchedulePolicy.REVIEW_STATUSES.contains(booking.getStatus())
                || !BookingSchedulePolicy.overdue(booking, clock.instant())) return;
        // Unresolved/received money must go through the existing reconciliation flow.
        if (hasFinancialActivity(booking)) return;
        BookingDetailsPayload details = BookingDetailsJson.read(json, booking);
        details.setScheduleChange(new HashMap<>(Map.of(
                "flaggedAt", clock.millis(), "previousStatus", booking.getStatus())));
        booking.setStatus(BookingSchedulePolicy.REQUIRED);
        releaseReservations(booking);
        history(details, "Hệ thống", "Ngày khởi hành dự kiến đã qua. Cần khách xác nhận lại lịch.");
        save(booking, details);
        String day = booking.getDepartureDate().atZone(BookingSchedulePolicy.VIETNAM)
                .toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        notify(booking, "customer", "Đơn BK-" + id + " cần xác nhận lại lịch",
                "Ngày khởi hành dự kiến " + day + " đã qua, nhưng đơn chưa hoàn tất chuẩn bị chuyến. "
                        + "Vui lòng chọn ngày mới, hủy yêu cầu hoặc liên hệ hỗ trợ.");
        notify(booking, "manager", "Đơn BK-" + id + " đã quá lịch dự kiến",
                "Đã thông báo khách xác nhận lại lịch. Chờ khách chọn ngày mới hoặc yêu cầu hỗ trợ.");
        mail.scheduleChangeRequired(booking);
    }

    @Transactional
    public void reschedule(Integer id, String username, LocalDate departureDate) {
        Booking booking = owned(id, username);
        requireEditable(booking);
        int leadDays = Integer.parseInt(config.getConfig("MIN_LEAD_DAYS").getConfigValue());
        LocalDate minimum = LocalDate.now(clock.withZone(BookingSchedulePolicy.VIETNAM))
                .plusDays(Math.max(1, leadDays));
        if (departureDate == null || departureDate.isBefore(minimum)) {
            throw new IllegalArgumentException("Ngày khởi hành mới phải từ "
                    + minimum.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ".");
        }
        BookingDetailsPayload details = BookingDetailsJson.read(json, booking);
        LocalDate previous = booking.getDepartureDate().atZone(BookingSchedulePolicy.VIETNAM).toLocalDate();
        releaseReservations(booking);
        // All review, route, staffing and pricing decisions depend on the requested day.
        details.setIntake(null);
        details.setMedical(Map.of("status", "pending"));
        details.setPlan(null);
        details.setRoute(null);
        details.setGate(null);
        details.setTrips(null);
        details.setQuote(null);
        details.setScheduleChange(null);
        if (details.getHorses() != null) details.getHorses().forEach(horse -> {
            if (horse.getInsurance() != null) horse.getInsurance().setReviewed(false);
        });
        booking.setRouteID(null);
        booking.setTotalBookingPrice(BigDecimal.ZERO);
        booking.setDepartureDate(departureDate.atStartOfDay(BookingSchedulePolicy.VIETNAM).toInstant());
        booking.setStatus("pending_intake");
        history(details, username, "Yêu cầu đổi ngày khởi hành từ " + previous + " sang " + departureDate
                + ". Chờ quản lý tiếp nhận và thẩm định lại.");
        save(booking, details);
        notify(booking, "manager", "Khách đã chọn lịch mới cho BK-" + id,
                "Ngày dự kiến mới: " + departureDate + ". Cần tiếp nhận và kiểm tra lại hồ sơ, xe và nhân sự.");
        notify(booking, "customer", "Đã nhận lịch mới cho BK-" + id,
                "Lịch " + departureDate + " đang chờ công ty kiểm tra và xác nhận.");
    }

    @Transactional
    public void cancel(Integer id, String username, String reason) {
        Booking booking = owned(id, username);
        requireEditable(booking);
        if (reason == null || reason.isBlank() || reason.length() > 500)
            throw new IllegalArgumentException("Vui lòng nhập lý do hủy, tối đa 500 ký tự.");
        BookingDetailsPayload details = BookingDetailsJson.read(json, booking);
        releaseReservations(booking);
        booking.setStatus("cancelled");
        history(details, username, "Hủy yêu cầu chưa thanh toán sau khi quá lịch: " + reason.trim());
        save(booking, details);
        notify(booking, "manager", "Khách đã hủy yêu cầu BK-" + id, reason.trim());
    }

    @Transactional
    public void requestSupport(Integer id, String username) {
        Booking booking = owned(id, username);
        if (!BookingSchedulePolicy.REQUIRED.equals(booking.getStatus()))
            throw new IllegalStateException("Đơn không ở bước cần xác nhận lại lịch.");
        BookingDetailsPayload details = BookingDetailsJson.read(json, booking);
        Map<String, Object> change = details.getScheduleChange() == null
                ? new HashMap<>() : new HashMap<>(details.getScheduleChange());
        if (change.containsKey("supportRequestedAt")) return;
        change.put("supportRequestedAt", clock.millis());
        details.setScheduleChange(change);
        history(details, username, "Yêu cầu quản lý liên hệ hỗ trợ sắp xếp lại lịch.");
        save(booking, details);
        notify(booking, "manager", "Khách cần hỗ trợ đổi lịch BK-" + id,
                "Vui lòng liên hệ khách để thống nhất ngày khởi hành mới.");
    }

    private Booking owned(Integer id, String username) {
        Booking booking = bookings.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng."));
        var owner = booking.getCustomerID() == null ? null : booking.getCustomerID().getUser();
        if (owner == null || username == null || (!username.equalsIgnoreCase(owner.getUsername())
                && !username.equalsIgnoreCase(owner.getEmail())))
            throw new AccessDeniedException("Bạn không có quyền thao tác trên đơn này.");
        return booking;
    }

    private boolean hasFinancialActivity(Booking booking) {
        return payments.findByBookingID_IdOrderByPaymentDateDesc(booking.getId()).stream()
                .anyMatch(payment -> !"FAILED".equalsIgnoreCase(payment.getStatus()));
    }

    private void requireEditable(Booking booking) {
        if (!BookingSchedulePolicy.REQUIRED.equals(booking.getStatus()))
            throw new IllegalStateException("Đơn không ở bước cần xác nhận lại lịch.");
        if (hasFinancialActivity(booking))
            throw new IllegalStateException("Đơn có giao dịch cần đối soát. Vui lòng liên hệ quản lý để xử lý lịch và thanh toán.");
    }

    private void releaseReservations(Booking booking) {
        vehicles.deleteByBookingID_Id(booking.getId());
        assignments.deleteByBookingID_Id(booking.getId());
        quotations.findByBookingIdOrderBySentAtDesc(booking.getId()).stream()
                .filter(quote -> "PENDING".equalsIgnoreCase(quote.getStatus())).forEach(quote -> {
                    quote.setStatus("EXPIRED");
                    quotations.save(quote);
                });
    }

    private void history(BookingDetailsPayload details, String actor, String text) {
        if (details.getHistory() == null) details.setHistory(new ArrayList<>());
        details.getHistory().add(Map.of("time", clock.millis(), "actor", actor, "text", text));
    }

    private void save(Booking booking, BookingDetailsPayload details) {
        try {
            booking.setDetailsJson(json.writeValueAsString(details));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Không thể lưu lịch đơn hàng.", exception);
        }
        bookings.save(booking);
    }

    private void notify(Booking booking, String role, String title, String message) {
        notifications.save(Notification.builder().recipientRole(role)
                .recipientUsername("customer".equals(role) ? booking.getCustomerID().getUser().getUsername() : null)
                .title(title).message(message).bookingId("BK-" + booking.getId())
                .link("customer".equals(role) ? "/orders/BK-" + booking.getId()
                        : "/manager/intake" + (BookingSchedulePolicy.REQUIRED.equals(booking.getStatus()) ? "?group=schedule" : ""))
                .isRead(false).createdAt(clock.instant()).build());
    }
}
