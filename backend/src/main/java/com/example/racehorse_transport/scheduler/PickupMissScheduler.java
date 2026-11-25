package com.example.racehorse_transport.scheduler;

import com.example.racehorse_transport.service.PickupMissService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PickupMissScheduler {
    private final PickupMissService misses;

    @Scheduled(cron = "${app.booking.pickup-miss-cron:0 * * * * *}")
    public void process() {
        for (Long horseId : misses.expired()) {
            try {
                misses.expire(horseId);
            } catch (RuntimeException ex) {
                log.error("Không xử lý được hạn chọn sau đón hụt cho ngựa {}", horseId, ex);
            }
        }
    }
}
