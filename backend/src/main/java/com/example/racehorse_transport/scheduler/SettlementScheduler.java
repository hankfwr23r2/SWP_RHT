package com.example.racehorse_transport.scheduler;

import com.example.racehorse_transport.service.SettlementFollowUpService;
import com.example.racehorse_transport.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SettlementScheduler {
    private final SettlementService settlements;
    private final SettlementFollowUpService followUps;

    @Scheduled(cron = "${app.settlement.auto-close-cron:0 30 0 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void closeDeliveredBookings() {
        each(followUps.resolvedIncidents(), followUps::closeResolvedIncident, "sự cố");
        each(followUps.deliveredBookings(), followUps::processDelivered, "đơn đã giao");
        followUps.remindOverdueRefunds();
        for (Long bookingId : settlements.autoCloseCandidates()) {
            try {
                settlements.autoClose(bookingId);
            } catch (RuntimeException ex) {
                log.error("Không thể tự đóng đơn {}", bookingId, ex);
            }
        }
    }

    private static void each(java.util.List<Long> ids, java.util.function.Consumer<Long> action, String what) {
        for (Long id : ids) {
            try {
                action.accept(id);
            } catch (RuntimeException ex) {
                log.error("Không thể xử lý {} {}", what, id, ex);
            }
        }
    }
}
