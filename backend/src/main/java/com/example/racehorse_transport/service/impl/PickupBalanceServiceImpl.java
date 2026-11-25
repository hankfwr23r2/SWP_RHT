package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.operation.BalanceHoldContactRequestDto;
import com.example.racehorse_transport.dto.operation.PickupHubTransferRequestDto;
import com.example.racehorse_transport.dto.operation.PickupWaitExtensionRequestDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.PaymentStatus;
import com.example.racehorse_transport.entity.enums.PaymentType;
import com.example.racehorse_transport.entity.enums.TripStopType;
import com.example.racehorse_transport.entity.enums.VehicleTripType;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.BalanceHoldPolicy;
import com.example.racehorse_transport.repository.AuditLogRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.PaymentRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.TripStopRepository;
import com.example.racehorse_transport.service.AuditService;
import com.example.racehorse_transport.service.PaymentCheckoutService;
import com.example.racehorse_transport.service.PickupBalanceService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PickupBalanceServiceImpl implements PickupBalanceService {
    private static final String BOOKING = "booking";
    private static final String EXTENDED = "PICKUP_BALANCE_WAIT_EXTENDED";
    private static final String HUB_TRANSFER = "UNCLAIMED_PICKUP_TO_HUB";

    private final BookingRepository bookings;
    private final HorseLegRepository legs;
    private final PaymentRepository payments;
    private final TripStopRepository stops;
    private final StaffRepository staff;
    private final AuditLogRepository logs;
    private final AuditService audit;
    private final PaymentCheckoutService checkout;
    private final SystemConfigService config;
    private final Clock clock;

    @Override
    @Transactional
    public void recordContact(Long bookingId, BalanceHoldContactRequestDto request, String username) {
        Booking booking = lock(bookingId);
        if (!bookings.hasReceivedHorses(bookingId) && bookings.findActiveBalanceHoldAt(bookingId).isEmpty()) {
            throw BookingRuleException.conflict("Chỉ ghi liên hệ thanh toán khi đã nhận ngựa.");
        }
        String action = BalanceHoldPolicy.CALL_ACTION_PREFIX + request.target();
        Instant minute = clock.instant().truncatedTo(ChronoUnit.MINUTES);
        if (!hasAction(booking, action, minute)) {
            audit.record(actorId(username), action, BOOKING, bookingId, null, Map.of("result", request.result().strip()));
        }
    }

    @Override
    @Transactional
    public void extend(Long bookingId, PickupWaitExtensionRequestDto request, String username) {
        var wait = waitingPickup(lock(bookingId));
        Instant now = clock.instant();
        if (!now.isBefore(wait.payment().getDueAt()) || hasAction(wait.booking(), EXTENDED, wait.since())
                || hasAction(wait.booking(), HUB_TRANSFER, wait.since())) {
            throw BookingRuleException.conflict("Đã hết giờ chờ hoặc đã sử dụng lần gia hạn.");
        }
        Instant until = wait.payment().getDueAt().plus(config.getInt("BALANCE_WAIT_MINUTES"), ChronoUnit.MINUTES);
        Duration delay = BalanceHoldPolicy.delay(wait.stop().getPlannedDepartureAt(), until);
        var departure = wait.booking().getDeparture();
        if (departure != null) {
            Instant deadline = departure.getPlannedDepartureAt().minus(config.getInt("HUB_CHECKIN_HOURS"), ChronoUnit.HOURS);
            if (wait.horses().stream().anyMatch(leg -> leg.getPlannedEndAt().plus(delay).isAfter(deadline))) {
                throw BookingRuleException.conflict("Gia hạn sẽ lỡ hạn đưa ngựa tới hub.");
            }
        }
        requireRideLimits(wait.stop(), delay, null, List.of());
        audit.record(actorId(username), EXTENDED, BOOKING, bookingId, Map.of("dueAt", wait.payment().getDueAt()),
                Map.of("dueAt", until, "reason", request.reason().strip()));
        wait.payment().setDueAt(until);
    }

    @Override
    @Transactional
    public void transferToHub(Long bookingId, PickupHubTransferRequestDto request, String username) {
        var wait = waitingPickup(lock(bookingId));
        var now = clock.instant();
        if (hasAction(wait.booking(), HUB_TRANSFER, wait.since())) return;
        if (now.isBefore(wait.payment().getDueAt()) || checkout.hasPendingBalanceConfirmation(bookingId)) {
            throw BookingRuleException.conflict("Chưa hết thời gian chờ thanh toán tại nơi đón.");
        }
        TripStop hub = stops.findByVehicleTrip_IdOrderBySequenceNumber(wait.stop().getVehicleTrip().getId()).stream()
                .filter(stop -> stop.getSequenceNumber() > wait.stop().getSequenceNumber() && stop.getStopType() == TripStopType.HUB
                        && stop.getActualArrivalAt() == null && stop.getLocation() != null)
                .findFirst().orElseThrow(() -> BookingRuleException.conflict("Cần xếp điểm hub an toàn trên lượt xe trước khi đưa ngựa về giữ."));
        requireRideLimits(wait.stop(), BalanceHoldPolicy.delay(wait.stop().getPlannedDepartureAt(), now), hub, wait.horses());
        for (HorseLeg leg : wait.horses()) {
            leg.setAlightTripStop(hub);
            leg.setToBookingAddress(null);
            leg.setToLocation(hub.getLocation());
            leg.setPlannedEndAt(hub.getPlannedArrivalAt());
        }
        audit.record(actorId(username), HUB_TRANSFER, BOOKING, bookingId, null,
                Map.of("hubStopId", hub.getId(), "reason", request.reason().strip()));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canLeaveForHub(Long bookingId) {
        return bookings.findWaitingPickupSince(bookingId).map(since ->
                logs.existsByTargetTableAndTargetIdAndActionAndCreatedAtGreaterThanEqual(BOOKING, bookingId, HUB_TRANSFER, since))
                .orElse(false);
    }

    private void requireRideLimits(TripStop pickup, Duration delay, TripStop hub, List<HorseLeg> diverted) {
        var trip = pickup.getVehicleTrip();
        long max = BalanceHoldPolicy.rideLimitSeconds(trip.getTripType() == VehicleTripType.DIRECT,
                config.getInt("MAX_DRIVE_HOURS_PER_DAY"));
        for (HorseLeg leg : legs.findActiveOnTrip(trip.getId(), Set.of(HorseLegStatus.FAILED, HorseLegStatus.CANCELLED))) {
            if (leg.getStatus() != HorseLegStatus.IN_PROGRESS || leg.getAlightTripStop() == null
                    || leg.getAlightTripStop().getSequenceNumber() <= pickup.getSequenceNumber()) continue;
            Instant end = diverted.contains(leg) ? hub.getPlannedArrivalAt() : leg.getAlightTripStop().getPlannedArrivalAt();
            if (Duration.between(leg.getActualStartAt(), end.plus(delay)).getSeconds() > max) {
                throw BookingRuleException.conflict("Phương án vượt thời gian ngồi xe của ngựa đã lên xe.");
            }
        }
    }

    private WaitingPickup waitingPickup(Booking booking) {
        var error = "Đơn chưa có ngựa đang chờ số dư tại điểm đón.";
        if (booking.getStatus() != BookingStatus.CONFIRMED) throw BookingRuleException.conflict(error);
        Instant since = bookings.findWaitingPickupSince(booking.getId()).orElseThrow(() -> BookingRuleException.conflict(error));
        var boarded = legs.findByBookingHorse_Booking_IdAndStatus(booking.getId(), HorseLegStatus.IN_PROGRESS).stream()
                .filter(leg -> leg.getBoardTripStop() != null && leg.getBoardTripStop().getStopType() == TripStopType.PICKUP
                        && leg.getBoardTripStop().getActualDepartureAt() == null).toList();
        if (boarded.isEmpty()) throw BookingRuleException.conflict(error);
        var stop = boarded.getFirst().getBoardTripStop();
        if (boarded.stream().anyMatch(leg -> !leg.getBoardTripStop().getId().equals(stop.getId())))
            throw BookingRuleException.conflict("Các con đang chờ ở nhiều điểm đón, cần xử lý theo lượt xe.");
        var payment = payments.findByBooking_IdOrderById(booking.getId()).stream()
                .filter(row -> row.getPaymentType() == PaymentType.BALANCE && row.getStatus() == PaymentStatus.PENDING && row.getDueAt() != null)
                .findFirst().orElseThrow(() -> BookingRuleException.conflict(error));
        return new WaitingPickup(booking, stop, boarded, payment, since);
    }

    private Booking lock(Long bookingId) {
        return bookings.findForUpdate(bookingId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn."));
    }

    private boolean hasAction(Booking booking, String action, Instant since) {
        return logs.existsByTargetTableAndTargetIdAndActionAndCreatedAtGreaterThanEqual(BOOKING, booking.getId(), action, since);
    }

    private Long actorId(String username) {
        return staff.findByUser_Username(username).orElseThrow(() -> new AccessDeniedException("Không tìm thấy hồ sơ nhân viên.")).getUserId();
    }

    private record WaitingPickup(Booking booking, TripStop stop, List<HorseLeg> horses, Payment payment, Instant since) {}
}
