package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.BookingStatusHistory;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.CostPayerType;
import com.example.racehorse_transport.entity.enums.IncidentStatus;
import com.example.racehorse_transport.entity.enums.PaymentStatus;
import com.example.racehorse_transport.entity.enums.PaymentType;
import com.example.racehorse_transport.policy.CostPayerPolicy;
import com.example.racehorse_transport.policy.SettlementPolicy;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.BookingStatusHistoryRepository;
import com.example.racehorse_transport.repository.CostItemRepository;
import com.example.racehorse_transport.repository.IncidentRepository;
import com.example.racehorse_transport.repository.PaymentRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.service.AuditService;
import com.example.racehorse_transport.service.NotificationService;
import com.example.racehorse_transport.service.SettlementFollowUpService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettlementFollowUpServiceImpl implements SettlementFollowUpService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final BookingRepository bookings;
    private final BookingStatusHistoryRepository history;
    private final CostItemRepository costs;
    private final IncidentRepository incidents;
    private final PaymentRepository payments;
    private final StaffRepository staff;
    private final NotificationService notifications;
    private final AuditService audit;
    private final SystemConfigService config;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<Long> deliveredBookings() {
        return history.findBookingIdsWithStatusBefore(BookingStatus.DELIVERED,
                clock.instant().minus(Duration.ofDays(config.getInt("COST_APPROVAL_REMINDER_1_DAYS"))));
    }

    @Override
    @Transactional
    public void processDelivered(Long bookingId) {
        var booking = bookings.findForUpdate(bookingId).orElse(null);
        if (booking == null || booking.getStatus() != BookingStatus.DELIVERED) return;
        Instant now = clock.instant();
        Instant delivered = history.findFirstByBooking_IdAndToStatusOrderByChangedAtDescIdDesc(bookingId, BookingStatus.DELIVERED)
                .map(BookingStatusHistory::getChangedAt).orElse(null);
        var waiting = costs.findByBooking_IdOrderByIncurredAt(bookingId).stream()
                .filter(cost -> CostPayerPolicy.customerSide(cost.getPayerType()) && !SettlementPolicy.isSettled(cost)
                        && cost.getApprovedAt() == null).toList();
        if (!waiting.isEmpty() && SettlementPolicy.elapsed(delivered, now, config.getInt("COST_APPROVAL_DAYS"))) {
            for (var row : waiting) {
                costs.findForUpdate(row.getId()).filter(cost -> cost.getApprovedAt() == null).ifPresent(cost -> {
                    cost.setPayerType(CostPayerType.COMPANY);
                    audit.record(null, "COST_APPROVAL_EXPIRED", "cost_item", cost.getId(), null,
                            Map.of("payerType", CostPayerType.COMPANY.name()));
                });
            }
            managers(bookingId, "Chi phí quá hạn duyệt chuyển công ty chịu", delivered, now,
                    "Đơn " + booking.getCode() + " có chi phí quá hạn duyệt, công ty chịu.");
        } else if (!waiting.isEmpty()) {
            for (String key : List.of("COST_APPROVAL_REMINDER_2_DAYS", "COST_APPROVAL_REMINDER_1_DAYS")) {
                int days = config.getInt(key);
                if (SettlementPolicy.elapsed(delivered, now, days)) {
                    managers(bookingId, "Chi phí chờ duyệt · ngày " + days, delivered, now,
                            "Đơn " + booking.getCode() + " đã giao còn chi phí chưa duyệt. Quá hạn thì công ty chịu.");
                    break;
                }
            }
        }
        boolean unresolved = incidents.findOpenForBooking(bookingId, List.of(IncidentStatus.REPORTED,
                IncidentStatus.PLAN_PENDING, IncidentStatus.PLAN_ACTIVE)).stream().findAny().isPresent();
        if (unresolved) {
            managers(bookingId, "Đơn đã giao còn sự cố chưa xử lý", startOfDay(now), now,
                    "Đơn " + booking.getCode() + " chưa đóng được vì còn sự cố chưa xử lý.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> resolvedIncidents() {
        return incidents.findResolvedBefore(IncidentStatus.RESOLVED,
                clock.instant().minus(Duration.ofDays(config.getInt("INCIDENT_AUTO_CLOSE_DAYS"))));
    }

    @Override
    @Transactional
    public void closeResolvedIncident(Long incidentId) {
        incidents.findForUpdate(incidentId).filter(row -> row.getStatus() == IncidentStatus.RESOLVED)
                .filter(row -> SettlementPolicy.elapsed(row.getResolvedAt(), clock.instant(), config.getInt("INCIDENT_AUTO_CLOSE_DAYS")))
                .filter(row -> costs.findByIncident_IdOrderByIncurredAt(incidentId).stream()
                        .allMatch(cost -> cost.getApprovedAt() != null || SettlementPolicy.isSettled(cost)))
                .ifPresent(row -> {
                    row.setStatus(IncidentStatus.CLOSED);
                    audit.record(null, "INCIDENT_AUTO_CLOSED", "incident", incidentId, null, null);
                });
    }

    @Override
    @Transactional
    public void remindOverdueRefunds() {
        Instant now = clock.instant();
        var pending = payments.findByPaymentTypeAndStatusOrderById(PaymentType.REFUND, PaymentStatus.PENDING);
        // Hạn chuyển khoản tính từ lần quét đầu tiên thấy khoản hoàn
        pending.stream().filter(refund -> refund.getDueAt() == null)
                .forEach(refund -> refund.setDueAt(now.plus(Duration.ofDays(config.getInt("REFUND_DUE_DAYS")))));
        pending.stream()
                .filter(refund -> refund.getDueAt() != null && !now.isBefore(refund.getDueAt()))
                .map(refund -> refund.getBooking()).distinct()
                .forEach(booking -> managers(booking.getId(), "Khoản hoàn quá hạn chuyển", startOfDay(now), now,
                        "Đơn " + booking.getCode() + " có khoản hoàn quá hạn. Chuyển khoản và ghi chứng từ."));
    }

    private void managers(Long bookingId, String title, Instant since, Instant now, String message) {
        if (bookings.hasNotificationBetween(bookingId, title, since, now.plusSeconds(60))) return;
        var ids = staff.findUserIdsByRoleAndStatus("MANAGER", AppUserStatus.ACTIVE);
        if (!ids.isEmpty()) notifications.notify(ids, bookingId, title, message, "/manager/orders/" + bookingId);
    }

    private static Instant startOfDay(Instant now) {
        return now.atZone(ZONE).toLocalDate().atStartOfDay(ZONE).toInstant();
    }
}
