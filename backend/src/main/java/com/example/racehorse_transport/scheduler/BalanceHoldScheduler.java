package com.example.racehorse_transport.scheduler;

import com.example.racehorse_transport.service.BalanceHoldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BalanceHoldScheduler {
    private final BalanceHoldService holds;

    @Scheduled(cron = "${app.booking.balance-hold-cron:0 * * * * *}")
    public void process() {
        for (Long bookingId : holds.candidates()) {
            try {
                holds.process(bookingId);
            } catch (RuntimeException ex) {
                log.error("Không xử lý được lịch giữ ngựa cho đơn {}", bookingId, ex);
            }
        }
    }
}
