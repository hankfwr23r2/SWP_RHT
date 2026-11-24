package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.enums.BookingHorseStatus;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.StallSeatPolicy;
import com.example.racehorse_transport.repository.BookingHorseRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.StaffRepository;
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
                || horse.getStatus() != BookingHorseStatus.ACTIVE || booking.getStatus() != BookingStatus.CONFIRMED) {
            throw BookingRuleException.conflict("Chỉ loại ngựa đang hoạt động lúc nhận, kèm người thao tác và lý do.");
        }
        horse.setStatus(BookingHorseStatus.EXCLUDED);
        if (booking.getDeparture() != null) {
            departures.release(booking.getDeparture().getId(), StallSeatPolicy.seats(horse.getStallType().getCode()));
        }
        var replacement = quotations.replaceAfterExclusion(horse);
        boolean overpaid = replacement.map(payments::reconcileBalance).map(amount -> amount.signum() < 0).orElse(false);
        if (overpaid) {
            replacement.ifPresent(refunds::refundOverpayment);
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
}
