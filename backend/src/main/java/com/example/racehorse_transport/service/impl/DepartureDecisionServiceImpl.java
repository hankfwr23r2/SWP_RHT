package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.departure.DepartureConfirmRunRequestDto;
import com.example.racehorse_transport.dto.departure.DepartureDecisionResponseDto;
import com.example.racehorse_transport.dto.departure.DepartureDecisionResponseDto.ReceivedBookingResponseDto;
import com.example.racehorse_transport.entity.AppUser;
import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.BookingHorseStatus;
import com.example.racehorse_transport.entity.enums.DepartureStatus;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.DepartureDecisionPolicy;
import com.example.racehorse_transport.policy.DepartureDelayPolicy;
import com.example.racehorse_transport.policy.StallSeatPolicy;
import com.example.racehorse_transport.repository.AppUserRepository;
import com.example.racehorse_transport.repository.BookingHorseRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.DepartureRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.service.AuditService;
import com.example.racehorse_transport.service.BookingRescheduleService;
import com.example.racehorse_transport.service.DepartureDecisionService;
import com.example.racehorse_transport.service.NotificationService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DepartureDecisionServiceImpl implements DepartureDecisionService {

    private final DepartureRepository departures;
    private final BookingRepository bookings;
    private final BookingHorseRepository horses;
    private final AppUserRepository users;
    private final StaffRepository staff;
    private final BookingRescheduleService reschedule;
    private final NotificationService notifications;
    private final AuditService audit;
    private final SystemConfigService config;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<DepartureDecisionResponseDto> decisions(LocalDate fromDate, LocalDate toDate) {
        if (toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.");
        }
        return toDtos(departures.search(null, null, fromDate, toDate).stream()
                .filter(departure -> departure.getStatus() != DepartureStatus.CANCELLED || departure.getAutoCancelledAt() != null).toList());
    }

    @Override
    @Transactional
    public DepartureDecisionResponseDto confirmRun(Long departureId, String username) {
        return confirmRun(departureId, username, null);
    }

    @Override
    @Transactional
    public DepartureDecisionResponseDto confirmRun(Long departureId, String username, DepartureConfirmRunRequestDto request) {
        AppUser actor = users.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản " + username));
        Departure departure = departures.findForUpdate(departureId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyến " + departureId));
        Instant now = clock.instant();
        reasonNotConfirmable(departure, config.getInt("DEPARTURE_DECISION_HOURS"), now)
                .ifPresent(reason -> {
                    throw new IllegalStateException(reason);
                });
        int previousCapacity = departure.getStallCapacity();
        departure.setRunConfirmedAt(now);
        departure.setRunConfirmedBy(actor);
        audit.record(actor.getId(), "CONFIRM_DEPARTURE_RUN", "departure", departureId,
                Map.of("runConfirmed", false, "stallCapacity", previousCapacity),
                Map.of("runConfirmed", true, "stallCapacity", departure.getStallCapacity()));
        notifyCoordinators(departure);
        return toDtos(List.of(departure)).getFirst();
    }

    // Không mở transaction ở đây: mỗi đơn nhả trong transaction riêng, một đơn lỗi không làm hỏng đơn khác
    @Override
    public int releaseUnconfirmed() {
        Instant threshold = clock.instant().plus(Duration.ofHours(config.getInt("DEPARTURE_DECISION_HOURS")));
        int released = 0;
        for (Long bookingId : bookings.findIdsForDecisionRelease(DepartureDecisionPolicy.RELEASABLE,
                DepartureDecisionPolicy.NOT_STARTED, threshold)) {
            try {
                if (reschedule.releaseAtDecision(bookingId)) {
                    released++;
                }
            } catch (RuntimeException e) {
                log.error("Không nhả được chỗ của đơn {}", bookingId, e);
            }
        }
        return released;
    }

    private List<DepartureDecisionResponseDto> toDtos(List<Departure> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(Departure::getId).toList();
        Map<Long, Integer> committed = new HashMap<>();
        for (var row : horses.countByDepartureAndStall(ids, DepartureDecisionPolicy.COMMITTED, BookingHorseStatus.ACTIVE)) {
            committed.merge(row.getDepartureId(),
                    Math.toIntExact(row.getHorseCount() * StallSeatPolicy.seats(row.getStallTypeCode())), Integer::sum);
        }
        Map<Long, Integer> uncommitted = new HashMap<>();
        for (var row : bookings.countByDeparture(ids, DepartureDecisionPolicy.UNCOMMITTED)) {
            uncommitted.put(row.getDepartureId(), Math.toIntExact(row.getBookingCount()));
        }
        int decisionHours = config.getInt("DEPARTURE_DECISION_HOURS");
        int minFillPercent = config.getInt("DEPARTURE_MIN_FILL_PERCENT");
        Instant now = clock.instant();
        return rows.stream().map(departure -> {
            int seats = committed.getOrDefault(departure.getId(), 0);
            return DepartureDecisionResponseDto.from(departure,
                    DepartureDecisionPolicy.decisionAt(departure.getPlannedDepartureAt(), decisionHours), seats,
                    uncommitted.getOrDefault(departure.getId(), 0),
                    DepartureDecisionPolicy.belowMinimum(seats, departure.getStallCapacity(), minFillPercent),
                    reasonNotConfirmable(departure, decisionHours, now).isEmpty(),
                    departure.getRunRemindedAt() != null && departure.getRunConfirmedAt() == null
                            && DepartureDelayPolicy.notStarted(departure.getStatus()),
                    DepartureDelayPolicy.notStarted(departure.getStatus()), receivedBookings(departure));
        }).toList();
    }

    private List<ReceivedBookingResponseDto> receivedBookings(Departure departure) {
        if (departure.getDelayedAt() == null || !DepartureDelayPolicy.notStarted(departure.getStatus())) {
            return List.of();
        }
        return bookings.findByDeparture_Id(departure.getId()).stream()
                .filter(booking -> DepartureDecisionPolicy.blocksCancellation(booking.getStatus())
                        || bookings.hasReceivedHorses(booking.getId()))
                .map(ReceivedBookingResponseDto::from).toList();
    }

    private java.util.Optional<String> reasonNotConfirmable(Departure departure, int decisionHours, Instant now) {
        Instant original = departure.getPlannedDepartureAt();
        Instant planningTime = now.isBefore(original) ? original
                : DepartureDelayPolicy.effectiveTime(original, departure.getRevisedDepartureAt());
        // Demo bỏ kiểm tra giờ: quyết định chạy ngay, không chờ tới 48 giờ cuối
        int hours = config.timeRulesSkipped() ? com.example.racehorse_transport.policy.DemoTimeRulePolicy.DECISION_HOURS : decisionHours;
        return DepartureDecisionPolicy.reasonNotConfirmable(departure.getStatus(), planningTime, hours,
                departure.getRunConfirmedAt() != null, now);
    }

    private void notifyCoordinators(Departure departure) {
        List<Long> coordinators = departure.getCoordinator() == null
                ? staff.findUserIdsByRoleAndStatus("COORDINATOR", AppUserStatus.ACTIVE)
                : List.of(departure.getCoordinator().getUserId());
        if (!coordinators.isEmpty()) {
            notifications.notify(coordinators, null, "Chuyến đã được xác nhận chạy",
                    "Lập lượt xe trục cho chuyến " + departure.getRoute().getName() + " ngày " + departure.getDepartureDate()
                            + " với " + departure.getStallCapacity() + " chỗ.",
                    "/coordinator/dispatch?departureId=" + departure.getId() + "&date=" + departure.getDepartureDate());
        }
    }
}
