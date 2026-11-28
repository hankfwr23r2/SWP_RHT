package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.departure.DepartureDelayRequestDto;
import com.example.racehorse_transport.dto.departure.DepartureResponseDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.DepartureCapacityPolicy;
import com.example.racehorse_transport.policy.DepartureDecisionPolicy;
import com.example.racehorse_transport.policy.DepartureDelayPolicy;
import com.example.racehorse_transport.policy.DepartureDelayPolicy.Thresholds;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.DepartureRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.service.DepartureDelayService;
import com.example.racehorse_transport.service.DeparturePlanShiftService;
import com.example.racehorse_transport.service.DepartureService;
import com.example.racehorse_transport.service.NotificationService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class DepartureDelayServiceImpl implements DepartureDelayService {
    private static final String LATE_REASON = "Chuyến chưa xuất phát theo lịch.";
    private static final String CANCEL_REASON = "Chuyến chậm và chưa có giờ chạy mới.";
    private static final DateTimeFormatter NOTICE_TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final DepartureRepository departures;
    private final BookingRepository bookings;
    private final StaffRepository staff;
    private final SystemConfigService configs;
    private final NotificationService notifications;
    private final DepartureService departureService;
    private final DeparturePlanShiftService planner;
    private final Clock clock;
    private final TransactionTemplate transactions;

    @Override
    @Transactional(readOnly = true)
    public Thresholds thresholds() {
        return new Thresholds(configs.getInt("DEPARTURE_RUN_REMINDER_HOURS"), configs.getInt("DEPARTURE_DELAY_GRACE_MINUTES"),
                configs.getInt("DEPARTURE_DELAY_RESCHEDULE_HOURS"), configs.getInt("DEPARTURE_DELAY_REFUND_HOURS"));
    }

    @Override
    @Transactional
    public DepartureResponseDto revise(Long departureId, DepartureDelayRequestDto request) {
        var lockedTrips = planner.lockAffectedTrips(departureId);
        var load = lock(departureId);
        var departure = load.departure();
        var now = clock.instant();
        DepartureDelayPolicy.requireRevisable(departure.getStatus(), departure.getPlannedDepartureAt(),
                request.revisedDepartureAt(), now);
        Instant current = DepartureDelayPolicy.effectiveTime(departure.getPlannedDepartureAt(), departure.getRevisedDepartureAt());
        if (request.revisedDepartureAt().equals(current) && request.reason().strip().equals(departure.getDelayReason())) {
            return describe(departure);
        }
        planner.shift(departureId, lockedTrips, Duration.between(current, request.revisedDepartureAt()));
        request.applyTo(departure);
        if (departure.getDelayedAt() == null) {
            departure.setDelayedAt(now);
        }
        notifyCustomers(load, "Đã dời giờ chuyến", "Giờ gốc: " + NOTICE_TIME.format(departure.getPlannedDepartureAt())
                + ". Giờ mới: " + NOTICE_TIME.format(request.revisedDepartureAt()) + ". Lý do: " + departure.getDelayReason());
        return describe(departure);
    }

    @Override
    public void scan() {
        Thresholds limits = thresholds();
        Instant now = clock.instant();
        var ids = departures.findDelayCandidates(DepartureDecisionPolicy.NOT_STARTED, now,
                now.plus(Duration.ofHours(limits.reminderHours())), now.minus(Duration.ofMinutes(limits.graceMinutes())),
                now.minus(Duration.ofHours(limits.refundHours())));
        for (Long id : ids) {
            try {
                transactions.executeWithoutResult(transaction -> updateMilestones(lock(id), clock.instant(), limits));
            } catch (RuntimeException e) {
                log.error("Không cập nhật được mốc chuyến trễ {}", id, e);
            }
        }
    }

    private void updateMilestones(LockedDeparture load, Instant now, Thresholds limits) {
        Departure departure = load.departure();
        if (!DepartureDelayPolicy.notStarted(departure.getStatus())) {
            return;
        }
        Instant original = departure.getPlannedDepartureAt();
        boolean confirmed = departure.getRunConfirmedAt() != null;
        if (departure.getRunRemindedAt() == null && DepartureDelayPolicy.needsReminder(original, confirmed, now, limits)) {
            departure.setRunRemindedAt(now);
            notifications.notifyManagers(null, "Chuyến chưa xác nhận chạy",
                    "Cần quyết định chạy cho chuyến " + departure.getRoute().getName() + " ngày " + departure.getDepartureDate() + ".",
                    "/manager/departures" + departureLink(departure));
        }
        boolean cancelDue = DepartureDelayPolicy.cancellationDue(original, departure.getRevisedDepartureAt(), now, limits);
        if (departure.getDelayedAt() == null && (cancelDue || DepartureDelayPolicy.isLate(original, confirmed, now, limits))) {
            departure.setDelayedAt(now);
            departure.setDelayReason(LATE_REASON);
            notifyCustomers(load, "Chuyến bị chậm", LATE_REASON);
        }
        if (!cancelDue || departure.getDelayEscalatedAt() != null) {
            return;
        }
        if (load.bookings().stream().anyMatch(booking -> DepartureDecisionPolicy.blocksCancellation(booking.getStatus())
                || bookings.hasReceivedHorses(booking.getId()))) {
            departure.setDelayEscalatedAt(now);
            notifyStaff(departure, "Cần xử lý chuyến chậm đã nhận ngựa", "Liên hệ khách và sắp xếp phương án cho chuyến ");
            return;
        }
        departureService.cancelForDelay(departure.getId(), CANCEL_REASON);
        departure.setAutoCancelledAt(now);
        departure.setDelayReason(CANCEL_REASON);
        notifyCustomers(load, "Chuyến đã hủy do chậm", "Xem lựa chọn đổi chuyến hoặc hoàn tiền trong trang đơn.");
    }

    private LockedDeparture lock(Long id) {
        List<Long> ids = bookings.findIdsByDeparture(id);
        ids.forEach(bookings::findForUpdate);
        Departure departure = departures.findForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyến " + id));
        List<Booking> attached = bookings.findByDeparture_Id(id);
        var locked = new HashSet<>(ids);
        if (attached.stream().anyMatch(booking -> !locked.contains(booking.getId()))) {
            throw BookingRuleException.conflict("Chuyến vừa có thêm đơn. Vui lòng thử lại.");
        }
        return new LockedDeparture(departure, attached);
    }

    private DepartureResponseDto describe(Departure departure) {
        Departure loaded = departures.findWithRoute(departure.getId()).orElseThrow();
        return DepartureResponseDto.from(loaded, DepartureCapacityPolicy.available(loaded.getStallCapacity(), loaded.getBookedStallCount()));
    }

    private void notifyCustomers(LockedDeparture load, String title, String message) {
        for (Booking booking : load.bookings()) {
            Long id = booking.getId();
            notifications.notify(List.of(booking.getCustomer().getUser().getId()), id, title,
                    "Đơn " + booking.getCode() + ". " + message, "/orders/" + id);
        }
    }

    private void notifyStaff(Departure departure, String title, String action) {
        String message = action + departure.getRoute().getName() + " ngày " + departure.getDepartureDate() + ".";
        notifications.notifyManagers(null, title, message, "/manager/departures" + departureLink(departure));
        var coordinator = departure.getCoordinator();
        var recipients = coordinator == null ? staff.findUserIdsByRoleAndStatus("COORDINATOR", AppUserStatus.ACTIVE)
                : List.of(coordinator.getUserId());
        if (!recipients.isEmpty()) {
            notifications.notify(recipients, null, title, message, "/coordinator/dispatch?date=" + departure.getDepartureDate());
        }
    }

    private static String departureLink(Departure departure) {
        return "?departureId=" + departure.getId() + "&date=" + departure.getDepartureDate();
    }

    private record LockedDeparture(Departure departure, List<Booking> bookings) {}
}
