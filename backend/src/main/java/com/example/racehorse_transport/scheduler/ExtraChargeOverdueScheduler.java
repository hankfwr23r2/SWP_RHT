package com.example.racehorse_transport.scheduler;

import com.example.racehorse_transport.service.ExtraChargeService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExtraChargeOverdueScheduler {
    private final ExtraChargeService extraCharges;

    @Scheduled(cron = "${app.payment.extra-charge-reminder-cron:0 0 8 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void remindOverdue() {
        extraCharges.remindOverdue();
    }
}
