package com.example.racehorse_transport.scheduler;

import com.example.racehorse_transport.service.TripProgressNoticeService;
import com.example.racehorse_transport.service.TripReturnService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TripProgressNoticeScheduler {
    private final TripProgressNoticeService notices;
    private final TripReturnService returns;

    @Scheduled(cron = "0 */5 * * * *")
    public void checkHeldTrips() {
        notices.checkHeldTrips();
    }

    @Scheduled(cron = "0 */5 * * * *")
    public void checkLateReturns() {
        returns.remindLateReturns();
    }
}
