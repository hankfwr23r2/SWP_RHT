package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.client.MailClient;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.HorseStay;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.PaymentStatus;
import com.example.racehorse_transport.entity.enums.PaymentType;
import com.example.racehorse_transport.policy.BalanceHoldPolicy;
import com.example.racehorse_transport.repository.BookingStatusHistoryRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.PaymentRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.service.BalanceHoldNoticeService;
import com.example.racehorse_transport.service.BookingCancellationService;
import com.example.racehorse_transport.service.NotificationService;
import com.example.racehorse_transport.service.PaymentCheckoutService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(propagation = Propagation.MANDATORY)
public class BalanceHoldNoticeServiceImpl implements BalanceHoldNoticeService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String WRITTEN = BalanceHoldPolicy.WRITTEN_NOTICE_TITLE;
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final StaffRepository staff;
    private final PaymentCheckoutService checkout;
    private final BookingCancellationService cancellations;
    private final SystemConfigService config;
    private final NotificationService notifications;
    private final MailClient mail;
    private final BookingStatusHistoryRepository history;
    private final com.example.racehorse_transport.service.UnclaimedSettlementService settlement;

    @Override
    public void remindBalance(Booking booking, Instant reminder, Instant cutoff, Instant now) {
        if (now.isBefore(cutoff)) {
            payments.findByBooking_IdOrderById(booking.getId()).stream()
                    .filter(payment -> payment.getPaymentType() == PaymentType.BALANCE && payment.getStatus() == PaymentStatus.PENDING)
                    .map(Payment::getDueAt).filter(dueAt -> dueAt != null && !now.isBefore(dueAt)).min(Instant::compareTo)
                    .ifPresent(dueAt -> remind(booking, "Số dư đã đến hạn",
                            "Vui lòng thanh toán số dư. Điều phối cần gọi khách và người liên hệ khẩn cấp.", dueAt, cutoff, true));
        }
        if (!now.isBefore(reminder) && now.isBefore(cutoff)) {
            remind(booking, "Nhắc lần cuối trước khi xếp ngựa", "Hãy trả số dư trước khi kết thúc xếp ngựa để giữ chuyến.",
                    reminder, cutoff, true);
        }
    }

    @Override
    public boolean waitingForGateway(Long bookingId, Instant cutoff, Instant now) {
        int grace = config.getInt("BALANCE_HUB_GATEWAY_GRACE_MINUTES");
        return BalanceHoldPolicy.waitingForGateway(now, cutoff, true, grace)
                && checkout.hasPendingBalanceConfirmationAt(bookingId, cutoff);
    }

    @Override
    public void holdStarted(Booking booking, Instant base) {
        String title = "Ngựa đang ở hub chờ chọn lại lịch";
        String message = "Ngựa vẫn được chăm sóc. Phí giữ từ " + base.atZone(ZONE).toLocalDateTime()
                + " là " + config.getInt("STABLING_DAILY_FEE") + " đ/con cho mỗi 24 giờ trọn vẹn. Bạn có thể đến nhận ngựa bất cứ lúc nào.";
        send(booking, title, message, true);
    }

    @Override
    public void releaseForUnpaidHub(Long bookingId) {
        cancellations.releaseUnpaidAtHub(bookingId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> expiredPickupReturns(Instant now) {
        var cutoff = now.minus(config.getInt("PRICE_HOLD_DAYS"), ChronoUnit.DAYS);
        return java.util.stream.Stream.concat(
                bookings.findScheduleChangesByReason(BalanceHoldPolicy.RETURNED_AT_PICKUP, cutoff).stream(),
                bookings.findReturnedBalanceHoldIds(cutoff).stream()).distinct().toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> waitingPickupBalances() {
        return bookings.findWaitingPickupBalanceIds();
    }

    @Override
    public void remindAtPickup(Booking booking, Instant now) {
        if (booking.getStatus() != BookingStatus.CONFIRMED) return;
        bookings.findWaitingPickupSince(booking.getId()).ifPresent(at -> {
            var entries = payments.findByBooking_IdOrderById(booking.getId());
            for (int minute : List.of(0, config.getInt("BALANCE_PICKUP_REMINDER_MID_MINUTES"),
                    config.getInt("BALANCE_PICKUP_REMINDER_LAST_MINUTES"))) {
                if (BalanceHoldPolicy.reached(now, at, minute, ChronoUnit.MINUTES)) {
                    remind(booking, "Nhắc số dư tại nơi đón · phút " + minute,
                            "Vui lòng thanh toán để xe đi tiếp. Điều phối cần gọi khách và người liên hệ khẩn cấp.",
                            at, now.plusSeconds(60), true);
                }
            }
            if (checkout.isBalanceOverdue(entries)) {
                remind(booking, "Hết giờ chờ thanh toán tại nơi đón",
                        "Điều phối xác nhận có người nhận lại ngựa và giấy tờ, hoặc sắp xếp đưa ngựa về hub.",
                        at, now.plusSeconds(60), false);
            }
        });
    }

    @Override
    public void cancelExpiredPickupReturn(Booking booking, Instant now) {
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            settleReturnedBalanceHold(booking);
            return;
        }
        if (booking.getStatus() != BookingStatus.SCHEDULE_CHANGE_REQUIRED) return;
        var returnedAt = bookings.findReturnedBalanceHoldAt(booking.getId()).or(() ->
                history.findFirstByBooking_IdOrderByChangedAtDescIdDesc(booking.getId())
                .filter(row -> BalanceHoldPolicy.RETURNED_AT_PICKUP.equals(row.getReason()))
                .map(com.example.racehorse_transport.entity.BookingStatusHistory::getChangedAt));
        returnedAt.filter(at -> BalanceHoldPolicy.reached(now, at, config.getInt("PRICE_HOLD_DAYS"), ChronoUnit.DAYS))
                .ifPresent(at -> {
                    cancellations.cancelUnansweredBalanceHold(booking.getId());
                    if (booking.getStatus() == BookingStatus.CANCELLED) settleReturnedBalanceHold(booking);
                });
    }

    @Override
    public void processHeld(Booking booking, List<HorseStay> held, Instant now, boolean paid, BigDecimal accrued) {
        boolean isBalanceHold = held.stream().anyMatch(stay -> stay.getBalanceHoldAt() != null);
        Instant base = held.stream().map(BalanceHoldNoticeServiceImpl::chargedFrom).min(Instant::compareTo).orElseThrow();
        if (now.isBefore(base)) return;

        Instant cancelAt = base.plus(config.getInt("STABLING_MAX_DAYS"), ChronoUnit.DAYS);
        var date = now.atZone(ZONE).toLocalDate();
        Instant morning = date.atTime(LocalTime.of(config.getInt("STABLING_REMINDER_HOUR"), 0)).atZone(ZONE).toInstant();
        // Nhắc hằng ngày sau giao hụt do lịch nhắc quá hạn lưu trú đảm nhiệm.
        if (isBalanceHold && !now.isBefore(morning) && now.isBefore(cancelAt)) {
            remind(booking, "Liên hệ nhận ngựa tại hub", "Phí giữ tạm tính " + accrued.toPlainString()
                    + " đ. Điều phối cần liên hệ khách hôm nay.", morning, morning.plus(1, ChronoUnit.DAYS), true);
        }
        if (isBalanceHold && !paid && !now.isBefore(cancelAt) && (booking.getStatus() == BookingStatus.SCHEDULE_CHANGE_REQUIRED
                || booking.getStatus() == BookingStatus.CONFIRMED && booking.getBookingType() == BookingType.IMPORT)) {
            Instant custodySince = held.stream().map(HorseStay::getActualStartAt).min(Instant::compareTo).orElseThrow();
            long attempts = bookings.countBalanceHoldContactsSince(booking.getId(), BalanceHoldPolicy.CALL_ACTION_PREFIX, custodySince);
            if (attempts >= config.getInt("BALANCE_HOLD_MIN_CONTACTS")) {
                cancellations.cancelUnansweredBalanceHold(booking.getId());
            } else {
                Instant start = date.atStartOfDay(ZONE).toInstant();
                remind(booking, "Cần tiếp tục liên hệ về ngựa đang giữ",
                        "Đã tới hạn phản hồi. Điều phối cần ghi đủ lần gọi trước khi hệ thống hủy đơn.",
                        start, start.plus(1, ChronoUnit.DAYS), false);
            }
        }

        Instant writtenAt = base.plus(config.getInt("STABLING_WRITTEN_NOTICE_DAYS"), ChronoUnit.DAYS);
        if (now.isBefore(writtenAt) || isBalanceHold && paid && booking.getStatus() == BookingStatus.CONFIRMED) return;
        remind(booking, WRITTEN, "Vui lòng nhận lại ngựa tại hub. Công ty có thể chở ngựa về địa chỉ người gửi với cước một lượt đón.",
                writtenAt, now.plusSeconds(60), true);
        Instant escalationBefore = now.minus(config.getInt("STABLING_MANAGER_NOTICE_DAYS"), ChronoUnit.DAYS);
        if (!isBalanceHold && bookings.firstNoticeSince(booking.getId(), WRITTEN, writtenAt)
                .filter(sent -> !sent.isAfter(escalationBefore)).isPresent()) {
            String title = "Cần quản lý xử lý ngựa chưa có người nhận";
            var managers = staff.findUserIdsByRoleAndStatus("MANAGER", AppUserStatus.ACTIVE);
            if (!managers.isEmpty() && !bookings.hasNotificationBetween(booking.getId(), title, writtenAt, now.plusSeconds(60))) {
                notifications.notify(managers, booking.getId(), title, "Ngựa còn được chăm sóc tại hub. Quản lý cần ghi kết quả bàn giao cùng tệp chứng minh.",
                        "/manager/orders/" + booking.getId());
            }
        }
    }

    private void settleReturnedBalanceHold(Booking booking) {
        var id = booking.getId();
        if (bookings.hasLatestStatusReason(id, BalanceHoldPolicy.CANCELLED_AFTER_HOLD)
                && bookings.findReturnedBalanceHoldAt(id).isPresent()) {
            settlement.settle(id);
        }
    }

    private static Instant chargedFrom(HorseStay stay) {
        return stay.getBalanceHoldAt() == null ? stay.getPlannedStartAt() : stay.getBalanceHoldAt();
    }

    private void remind(Booking booking, String title, String message, Instant from, Instant to, boolean email) {
        if (!bookings.hasNotificationBetween(booking.getId(), title, from, to)) {
            send(booking, title, message, email);
        }
    }

    private void send(Booking booking, String title, String message, boolean email) {
        var customer = booking.getCustomer().getUser();
        notifications.notify(List.of(customer.getId()), booking.getId(), title, message, "/orders/" + booking.getId());
        var coordinators = staff.findUserIdsByRoleAndStatus("COORDINATOR", AppUserStatus.ACTIVE);
        if (!coordinators.isEmpty()) {
            notifications.notify(coordinators, booking.getId(), title + " · điều phối", message, null);
        }
        if (email) {
            try {
                mail.send(customer.getEmail(), title, message);
            } catch (MailException ex) {
                log.warn("Không gửi được email giữ ngựa cho đơn {}", booking.getId());
            }
        }
    }
}
