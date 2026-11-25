package com.example.racehorse_transport.scheduler;

import com.example.racehorse_transport.service.PickupMissFollowUpService;
import com.example.racehorse_transport.service.PickupMissService;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PickupMissScheduler {
    private final PickupMissService misses;
    private final PickupMissFollowUpService followUps;

    @Scheduled(cron = "${app.booking.pickup-miss-cron:0 * * * * *}")
    public void process() {
        each(misses.expired(), misses::expire, "hạn chọn sau đón hụt cho ngựa");
        each(followUps.candidates(), followUps::process, "hạn chọn lại lịch cho đơn");
    }

    private static void each(List<Long> ids, Consumer<Long> action, String what) {
        for (Long id : ids) {
            try {
                action.accept(id);
            } catch (RuntimeException ex) {
                log.error("Không xử lý được {} {}", what, id, ex);
            }
        }
    }
}
