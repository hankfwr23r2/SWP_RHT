package com.example.racehorse_transport.scheduler;

import com.example.racehorse_transport.policy.BookingDeadlinePolicy;
import com.example.racehorse_transport.service.BookingDeadlineReminderService;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingDeadlineReminderScheduler {
    private final BookingDeadlineReminderService reminders;

    @Scheduled(cron = "${app.booking.deadline-reminder-cron:0 0 * * * *}", zone = BookingDeadlinePolicy.BUSINESS_ZONE)
    public void sendReminders() {
        each(reminders.overdueBookingIds(), reminders::remind);
    }

    private static void each(List<Long> bookingIds, Consumer<Long> remind) {
        for (Long bookingId : bookingIds) {
            try {
                remind.accept(bookingId);
            } catch (RuntimeException ex) {
                log.error("Không thể nhắc việc quá hạn cho đơn {}", bookingId, ex);
            }
        }
    }
}
