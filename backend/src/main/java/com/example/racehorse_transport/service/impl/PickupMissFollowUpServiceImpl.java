package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.BookingHorseStatus;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.PickupMissLiability;
import com.example.racehorse_transport.policy.BalanceHoldPolicy;
import com.example.racehorse_transport.policy.PickupMissPolicy;
import com.example.racehorse_transport.repository.BookingHorseRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.BookingStatusHistoryRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.service.BookingCancellationService;
import com.example.racehorse_transport.service.NotificationService;
import com.example.racehorse_transport.service.PickupMissFollowUpService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PickupMissFollowUpServiceImpl implements PickupMissFollowUpService {
    private static final String REMINDER_TITLE = "Nhắc chọn lại lịch · ngày ";
    private static final String LIABILITY_TITLE = "Cần chốt bên chịu đón hụt để kết thúc đơn";

    private final BookingRepository bookings;
    private final BookingHorseRepository horses;
    private final BookingStatusHistoryRepository history;
    private final StaffRepository staff;
    private final BookingCancellationService cancellations;
    private final NotificationService notifications;
    private final SystemConfigService config;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<Long> candidates() {
        Instant cutoff = clock.instant().minus(Duration.ofDays(reminderDays().stream().min(Integer::compare).orElseThrow()));
        return Stream.concat(bookings.findScheduleChangesByReason(PickupMissPolicy.RELEASED_REASON, cutoff).stream(),
                bookings.findScheduleChangesByReason(BalanceHoldPolicy.RETURNED_AT_PICKUP, cutoff).stream()).distinct().toList();
    }

    @Override
    @Transactional
    public void process(Long bookingId) {
        Booking booking = bookings.findForUpdate(bookingId).orElse(null);
        if (booking == null || booking.getStatus() != BookingStatus.SCHEDULE_CHANGE_REQUIRED) {
            return;
        }
        var latest = history.findFirstByBooking_IdOrderByChangedAtDescIdDesc(bookingId).orElse(null);
        if (latest == null) {
            return;
        }
        boolean missed = PickupMissPolicy.RELEASED_REASON.equals(latest.getReason());
        if (!missed && !BalanceHoldPolicy.RETURNED_AT_PICKUP.equals(latest.getReason())) {
            return;
        }
        Instant now = clock.instant();
        Instant since = latest.getChangedAt();
        int holdDays = config.getInt("PRICE_HOLD_DAYS");
        if (!PickupMissPolicy.choiceWindowEnded(since, now, holdDays)) {
            remind(booking, since, now, holdDays);
        } else if (missed) {
            close(booking, now);
        }
    }

    private void remind(Booking booking, Instant since, Instant now, int holdDays) {
        PickupMissPolicy.reminderDay(since, now, reminderDays()).ifPresent(day -> {
            String title = REMINDER_TITLE + day;
            if (!bookings.hasNotificationBetween(booking.getId(), title, since, now.plusSeconds(60))) {
                notifications.notify(List.of(booking.getCustomer().getUser().getId()), booking.getId(), title,
                        "Đơn " + booking.getCode() + " chưa chọn lịch mới. Giá được giữ " + holdDays
                                + " ngày kể từ lần không đón được ngựa.", "/orders/" + booking.getId());
            }
        });
    }

    private void close(Booking booking, Instant now) {
        var liabilities = horses.findByBooking_Id(booking.getId()).stream()
                .filter(horse -> horse.getStatus() == BookingHorseStatus.ACTIVE && horse.getPickupMissedAt() != null)
                .map(BookingHorse::getPickupMissLiability).toList();
        if (liabilities.isEmpty() || liabilities.contains(null)) {
            if (!bookings.hasNotificationBetween(booking.getId(), LIABILITY_TITLE, now.minus(Duration.ofDays(1)), now.plusSeconds(60))) {
                notifications.notify(staff.findUserIdsByRoleAndStatus("MANAGER", AppUserStatus.ACTIVE), booking.getId(),
                        LIABILITY_TITLE, "Đơn " + booking.getCode() + " đã hết hạn chọn lịch nhưng chưa chốt bên chịu.", null);
            }
            return;
        }
        // Chỉ mất cọc khi mọi con hụt đều do khách
        boolean customerLiable = liabilities.stream().allMatch(PickupMissLiability.CUSTOMER::equals);
        cancellations.cancelAfterPickupMiss(booking.getId(), customerLiable, null);
        notifications.notify(List.of(booking.getCustomer().getUser().getId()), booking.getId(), "Đơn đã hủy vì hết hạn chọn lịch",
                customerLiable ? "Đơn " + booking.getCode() + " đã hủy, tiền cọc không được hoàn."
                        : "Đơn " + booking.getCode() + " đã hủy, toàn bộ số đã trả sẽ được hoàn.", "/orders/" + booking.getId());
    }

    private List<Integer> reminderDays() {
        return IntStream.rangeClosed(1, 4).mapToObj(i -> config.getInt("SCHEDULE_CHOICE_REMINDER_" + i + "_DAYS")).toList();
    }
}
