package com.example.racehorse_transport.scheduler;

import com.example.racehorse_transport.service.ScheduleChangeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentReminderScheduler {
    private final ScheduleChangeService schedules;

    @Scheduled(cron = "${app.booking.document-reminder-cron:0 0 8 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void sendReminders() {
        for (Long bookingId : schedules.reminderCandidates()) {
            try {
                schedules.remind(bookingId);
            } catch (RuntimeException ex) {
                log.error("Không thể nhắc giấy tờ cho đơn {}", bookingId, ex);
            }
        }
    }
}
