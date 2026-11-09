package com.example.racehorse_transport.scheduler;

import com.example.racehorse_transport.service.BookingScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingScheduleScheduler {
    private final BookingScheduleService schedules;

    @Scheduled(fixedDelayString = "${app.booking.schedule-scan-ms:30000}", initialDelay = 15000)
    public void scan() {
        for (Integer id : schedules.candidates()) {
            try {
                schedules.flagOverdue(id);
            } catch (RuntimeException exception) {
                log.error("Không cập nhật được lịch đơn BK-{}", id, exception);
            }
        }
    }
}
