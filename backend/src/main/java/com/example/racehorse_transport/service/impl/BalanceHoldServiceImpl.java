package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Handover;
import com.example.racehorse_transport.entity.HorseStay;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.entity.enums.HandoverStatus;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.HorseStayType;
import com.example.racehorse_transport.entity.enums.LocationType;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.HandoverRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.HorseStayRepository;
import com.example.racehorse_transport.service.BalanceHoldNoticeService;
import com.example.racehorse_transport.service.BalanceHoldService;
import com.example.racehorse_transport.service.HorseStayService;
import com.example.racehorse_transport.service.PaymentReadinessService;
import com.example.racehorse_transport.service.SystemConfigService;
import com.example.racehorse_transport.service.StablingChargeService;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BalanceHoldServiceImpl implements BalanceHoldService {
    private static final Set<HorseLegStatus> BOARDING = Set.of(HorseLegStatus.PLANNED, HorseLegStatus.IN_PROGRESS);

    private final BookingRepository bookings;
    private final HorseStayRepository stays;
    private final HorseLegRepository legs;
    private final HandoverRepository handovers;
    private final BalanceHoldNoticeService notices;
    private final PaymentReadinessService readiness;
    private final SystemConfigService config;
    private final Clock clock;
    private final HorseStayService horseStays;
    private final StablingChargeService charges;

    @Override
    @Transactional(readOnly = true)
    public List<Long> candidates() {
        return java.util.stream.Stream.of(stays.findBalanceHoldCandidates(), notices.waitingPickupBalances(),
                notices.expiredPickupReturns(clock.instant()), stays.findUnbilledReturnedBalanceHoldIds())
                .flatMap(List::stream).distinct().sorted().toList();
    }

    @Override
    @Transactional
    public void process(Long bookingId) {
        // HUB_OUT completion locks the handover before taking booking locks.
        var pending = handovers.findPendingHubOutIds(bookingId).stream()
                .map(handovers::findForUpdate).flatMap(java.util.Optional::stream).toList();
        Booking booking = bookings.findForUpdate(bookingId).orElse(null);
        if (booking == null) return;
        charges.chargeReturnedBalanceHolds(bookingId);

        Instant now = clock.instant();
        var active = stays.findByBookingHorse_Booking_Id(bookingId).stream()
                .filter(stay -> stay.getActualStartAt() != null && stay.getActualEndAt() == null)
                .filter(stay -> stay.getLocation().getLocationType() == LocationType.HUB)
                .toList();
        if (active.isEmpty()) {
            if (!readiness.isPaidInFull(bookingId)) notices.remindAtPickup(booking, now);
            notices.cancelExpiredPickupReturn(booking, now);
            return;
        }

        var held = active.stream().filter(stay -> stay.getStayType() == HorseStayType.STABLING).toList();
        boolean paid = readiness.isPaidInFull(bookingId);
        if (!held.isEmpty()) {
            var balance = held.stream().filter(stay -> stay.getBalanceHoldAt() != null).toList();
            if (!balance.isEmpty()) notices.processHeld(booking, balance, now, paid, charges.accrued(balance, now));
            var failed = held.stream().filter(stay -> stay.getBalanceHoldAt() == null).toList();
            for (var stay : failed) {
                var oneHorse = List.of(stay);
                notices.processHeld(booking, oneHorse, now, paid, charges.accrued(oneHorse, now));
            }
            charges.warnBeforeNextBlock(held, now);
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED || paid) return;
        var receiving = active.stream().filter(stay -> stay.getStayType() == HorseStayType.ASSEMBLY).toList();
        if (receiving.isEmpty() || legs.findByBookingHorse_Booking_IdAndStatus(bookingId, HorseLegStatus.IN_PROGRESS)
                .stream().findAny().isPresent()) return;

        var departure = booking.getDeparture();
        boolean imported = booking.getBookingType() == BookingType.IMPORT;
        boolean hasNextLinehaul = !imported && departure != null;
        Instant base = !hasNextLinehaul
                ? receiving.stream().map(HorseStay::getActualStartAt).max(Instant::compareTo).orElseThrow()
                        .plus(config.getInt("BALANCE_IMPORT_HUB_HOURS"), ChronoUnit.HOURS)
                : departure.getPlannedDepartureAt();
        Instant cutoff = hasNextLinehaul ? base.minus(config.getInt("BALANCE_HUB_LOADING_HOURS"), ChronoUnit.HOURS) : base;
        Instant reminder = base.minus(config.getInt("BALANCE_HUB_FINAL_REMINDER_HOURS"), ChronoUnit.HOURS);
        notices.remindBalance(booking, reminder, cutoff, now);
        if (now.isBefore(cutoff) || notices.waitingForGateway(bookingId, cutoff, now)) return;

        if (!imported) notices.releaseForUnpaidHub(bookingId);
        for (Handover row : pending) {
            if (row.getStatus() == HandoverStatus.PENDING
                    && legs.findByBoardTripStop_IdAndStatusIn(row.getTripStop().getId(), BOARDING).isEmpty()) {
                row.setStatus(HandoverStatus.FAILED);
                row.setHandedAt(now);
            }
        }
        horseStays.openBalanceHold(bookingId, base, hasNextLinehaul ? departure : null);
        notices.holdStarted(booking, base);
    }
}
