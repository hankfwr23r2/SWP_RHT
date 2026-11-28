package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.enums.TransportMode;
import com.example.racehorse_transport.dto.operation.PickupMissPriceResponseDto;
import com.example.racehorse_transport.dto.operation.PickupAddOnResponseDto;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.Quotation;
import java.util.List;
import java.util.Optional;
import java.time.Instant;

import com.example.racehorse_transport.entity.enums.TransportMode;
import com.example.racehorse_transport.entity.enums.BookingHorseStatus;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.StallSeatPolicy;
import com.example.racehorse_transport.policy.PickupMissPricingPolicy;
import com.example.racehorse_transport.repository.BookingHorseRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingHorseServiceImpl implements BookingHorseService {
    private final BookingRepository bookings;
    private final BookingHorseRepository horses;
    private final DepartureService departures;
    private final QuotationDraftService quotations;
    private final PaymentService payments;
    private final RefundService refunds;
    private final StaffRepository staff;
    private final NotificationService notifications;
    private final HorseLegRepository legs;
    private final BookingStatusService statuses;

    @Override
    @Transactional
    public void exclude(Long id, String reason, Long actorUserId) {
        Long bookingId = horses.findBookingId(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa trong đơn."));
        var booking = bookings.findForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn."));
        var horse = horses.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa trong đơn."));
        if (horse.getStatus() == BookingHorseStatus.EXCLUDED) {
            return;
        }
        if (actorUserId == null || reason == null || reason.isBlank() || reason.strip().length() > 500
                || horse.getStatus() != BookingHorseStatus.ACTIVE || (booking.getStatus() != BookingStatus.CONFIRMED && booking.getStatus() != BookingStatus.IN_OPERATION)) {
            throw BookingRuleException.conflict("Chỉ loại ngựa đang hoạt động lúc nhận, kèm người thao tác và lý do.");
        }
        horse.setStatus(BookingHorseStatus.EXCLUDED);
        if (booking.getDeparture() != null) {
            if (booking.getTransportMode() == TransportMode.SHARED) {
                departures.release(booking.getDeparture().getId(), StallSeatPolicy.seats(horse.getStallType().getCode()));
            } else if (horses.findForDetail(bookingId).stream().noneMatch(row -> row.getStatus() == BookingHorseStatus.ACTIVE)) {
                departures.releasePrivate(booking.getDeparture().getId(), bookingId);
            }
        }
        var replacement = quotations.replaceAfterExclusion(horse);
        boolean overpaid = replacement.map(this::settle).orElse(false);
        if (booking.getStatus() == BookingStatus.IN_OPERATION && legs.countActiveHorsesNotDelivered(bookingId) == 0) {
            statuses.transition(bookingId, BookingStatus.DELIVERED, "Đã giao các con còn lại trong đơn.", actorUserId);
        }
        var message = "Không nhận ngựa " + horse.getHorse().getName() + ". Lý do: " + reason.strip()
                + (overpaid ? ". Khách đã trả dư so với báo giá thay thế. Kiểm tra khoản hoàn đang chờ." : "");
        var recipients = staff.findUserIdsByRoleAndStatus("MANAGER", AppUserStatus.ACTIVE);
        if (recipients.isEmpty()) {
            log.warn("Không có quản lý đang hoạt động để nhận thông báo loại ngựa của đơn {}", bookingId);
            return;
        }
        notifications.notify(recipients, bookingId, "Loại ngựa lúc nhận", message, null);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void repricePickup(BookingHorse horse, HorseLeg retry, List<BookingHorse> riding, boolean waivePickup) {
        bookings.findForUpdate(horse.getBooking().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn."));
        quotations.replaceAfterPickupChoice(horse, retry, riding, waivePickup, false, false).ifPresent(quotation -> {
            settle(quotation);
            if (retry != null && retry.getActualStartAt() != null) {
                payments.requestBalance(horse.getBooking().getId(), retry.getActualStartAt());
            }
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PickupMissPriceResponseDto> previewPickup(BookingHorse horse, HorseLeg retry,
            List<BookingHorse> riding, boolean waivePickup, boolean exclude) {
        return quotations.replaceAfterPickupChoice(horse, retry, riding, waivePickup, exclude, true)
                .map(quotation -> {
                    var settlement = PickupMissPricingPolicy.settlement(payments.outstanding(quotation));
                    return PickupMissPriceResponseDto.from(quotation, settlement.balanceDue(), settlement.refund());
                });
    }

    private boolean settle(Quotation quotation) {
        boolean overpaid = payments.reconcileBalance(quotation).signum() < 0;
        if (overpaid) refunds.refundOverpayment(quotation);
        return overpaid;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PickupAddOnResponseDto> pickupAddOns(Long horseId) {
        return quotations.pickupAddOns(horseId);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public PickupAddOnResponseDto completePickupAddOn(Long horseId, Long serviceId, Instant at) {
        return quotations.completePickupAddOn(horseId, serviceId, at);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void reviseExcludedInsurance(BookingHorse horse) {
        quotations.replaceAfterExclusion(horse).ifPresent(this::settle);
    }
}
