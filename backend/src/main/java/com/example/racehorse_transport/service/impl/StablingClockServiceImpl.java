package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.health.StablingClockResponseDto;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.HorseStay;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.HorseStayType;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.HorseStayRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.service.NotificationService;
import com.example.racehorse_transport.service.RedeliveryChargeService;
import com.example.racehorse_transport.service.StablingChargeService;
import com.example.racehorse_transport.service.StablingClockService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StablingClockServiceImpl implements StablingClockService {
    private final BookingRepository bookings;
    private final HorseStayRepository stays;
    private final HorseLegRepository legs;
    private final com.example.racehorse_transport.service.CoordinatorRecipientService coordinatorRecipients;
    private final StablingChargeService charges;
    private final RedeliveryChargeService redelivery;
    private final NotificationService notifications;
    private final SystemConfigService config;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<StablingClockResponseDto> forBooking(Long bookingId, String username) {
        bookings.findByIdAndCustomer_User_Username(bookingId, username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn."));
        Instant now = clock.instant();
        return stays.findByBookingHorse_Booking_Id(bookingId).stream().filter(StablingClockServiceImpl::active)
                .map(stay -> charges.clock(stay, now, canReady(stay, now), canCollect(stay, now))).toList();
    }

    @Override
    @Transactional
    public void ready(Long stayId, String username, String callNote) {
        var stay = lockActive(stayId);
        if (callNote == null) requireOwner(stay, username);
        Instant now = clock.instant();
        if (stay.getReadyAt() != null) return;
        if (!canReady(stay, now)) {
            throw new DispatchRuleException("stayId", "Ngựa đang có hẹn tự nhận hoặc đang giữ do chưa trả số dư.", true);
        }
        Long actor = callNote == null ? null : coordinatorRecipients.staffByUsername(username).getUserId();
        charges.chargeThrough(stay, now, actor);
        stay.setCollectionReportedAt(null);
        stay.setCollectionExpectedAt(null);
        stay.setReadyAt(now);
        stay.setReadyCallNote(callNote == null ? null : callNote.strip());
        plannedRedelivery(stay).ifPresent(redelivery::chargeReadyTrip);
    }

    @Override
    @Transactional
    public void laterSlot(Long stayId, Instant earliest, Instant chosen) {
        var stay = lockActive(stayId);
        if (stay.getReadyAt() == null || earliest.isBefore(stay.getReadyAt()) || !chosen.isAfter(earliest)
                || chosen.isBefore(clock.instant())) {
            throw new DispatchRuleException("chosenAt", "Khung khách chọn phải sau khung sớm nhất đã đưa.");
        }
        if (stay.getEarliestRedeliveryAt() != null && !stay.getEarliestRedeliveryAt().equals(earliest)) {
            throw new DispatchRuleException("earliestAt", "Khung sớm nhất đã được ghi.", true);
        }
        stay.setEarliestRedeliveryAt(earliest);
        stay.setChosenRedeliveryAt(chosen);
    }

    @Override
    @Transactional
    public void collect(Long stayId, String username, Instant expectedAt) {
        var stay = lockActive(stayId);
        requireOwner(stay, username);
        Instant now = clock.instant();
        if (!canCollect(stay, now) || expectedAt.isBefore(now)
                || expectedAt.isAfter(now.plus(config.getInt("STABLING_COLLECTION_ADVANCE_HOURS"), ChronoUnit.HOURS))) {
            throw new DispatchRuleException("expectedAt", "Chỉ được báo giờ tự đến trong 24 giờ tới khi chưa có hẹn.");
        }
        stay.setCollectionReportedAt(now);
        stay.setCollectionExpectedAt(expectedAt);
    }

    @Override
    @Transactional
    public void remindMissingRedelivery() {
        Instant now = clock.instant();
        Instant deadline = now.minus(config.getInt("STABLING_REDELIVERY_OFFER_HOURS"), ChronoUnit.HOURS);
        var byBooking = java.util.Comparator.comparing((HorseStay row) -> row.getBookingHorse().getBooking().getId());
        for (var stay : stays.findByReadyAtIsNotNullAndActualEndAtIsNull().stream().sorted(byBooking).toList()) {
            var booking = stay.getBookingHorse().getBooking();
            bookings.findForUpdate(booking.getId()).orElseThrow();
            var current = stays.findForUpdate(stay.getId()).filter(StablingClockServiceImpl::active).orElse(null);
            if (current == null) continue;
            charges.chargeAfterReadiness(current, now);
            if (current.getReadyAt().isAfter(deadline)) continue;
            String title = "Chưa lập lượt giao lại · " + stay.getId();
            if (plannedRedelivery(current).isPresent()
                    || bookings.hasNotificationBetween(booking.getId(), title, current.getReadyAt(), now)) continue;
            notifications.notify(coordinatorRecipients.forHorse(current.getBookingHorse(), null), booking.getId(), title,
                    "Khách đã sẵn sàng nhận ngựa. Hãy đưa khung giao lại cho đơn " + booking.getCode() + ".", "/coordinator/hub");
        }
    }

    private HorseStay lockActive(Long stayId) {
        var bookingId = stays.findBookingId(stayId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lưu trú."));
        bookings.findForUpdate(bookingId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn."));
        return stays.findForUpdate(stayId).filter(StablingClockServiceImpl::active)
                .orElseThrow(() -> new DispatchRuleException("stayId", "Ngựa không còn được giữ tại hub.", true));
    }

    private static void requireOwner(HorseStay stay, String username) {
        if (!Objects.equals(stay.getBookingHorse().getBooking().getCustomer().getUser().getUsername(), username)) {
            throw new AccessDeniedException("Bạn không có quyền thao tác trên đơn này.");
        }
    }

    private static boolean active(HorseStay stay) {
        return stay.getStayType() == HorseStayType.STABLING && stay.getActualStartAt() != null && stay.getActualEndAt() == null;
    }

    private boolean canReady(HorseStay stay, Instant now) {
        return stay.getBalanceHoldAt() == null && stay.getReadyAt() == null && canCollect(stay, now);
    }

    private boolean canCollect(HorseStay stay, Instant now) {
        return stay.getCollectionExpectedAt() == null || now.isAfter(stay.getCollectionExpectedAt()
                .plus(config.getInt("STABLING_COLLECTION_GRACE_HOURS"), ChronoUnit.HOURS));
    }

    private Optional<HorseLeg> plannedRedelivery(HorseStay stay) {
        return legs.findByBookingHorse_IdOrderBySequenceNumber(stay.getBookingHorse().getId()).stream()
                .filter(leg -> leg.getStatus() == HorseLegStatus.PLANNED && leg.getReplacedHorseLeg() != null
                        && (stay.getFailedDeliveryLeg() != null
                        ? stay.getFailedDeliveryLeg().getId().equals(leg.getReplacedHorseLeg().getId())
                        : leg.getPlannedStartAt() != null && !leg.getPlannedStartAt().isBefore(stay.getActualStartAt())))
                .findFirst();
    }
}
