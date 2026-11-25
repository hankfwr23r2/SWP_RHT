package com.example.racehorse_transport.scheduler;

import com.example.racehorse_transport.service.CustomerDebtService;
import com.example.racehorse_transport.service.ExtraChargeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExtraChargeOverdueScheduler {
    private final ExtraChargeService extraCharges;
    private final CustomerDebtService debts;

    @Scheduled(cron = "${app.payment.extra-charge-reminder-cron:0 0 8 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void remindOverdue() {
        extraCharges.remindOverdue();
        for (Long paymentId : debts.followUpCandidates()) {
            try {
                debts.followUp(paymentId);
            } catch (RuntimeException ex) {
                log.error("Không xử lý được công nợ của khoản thu {}", paymentId, ex);
            }
        }
    }
}
