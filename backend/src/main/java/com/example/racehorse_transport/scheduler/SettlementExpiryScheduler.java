package com.example.racehorse_transport.scheduler;

import com.example.racehorse_transport.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementExpiryScheduler {

    private final BookingService bookingService;

    // Chạy định kỳ mỗi 5 phút (300.000 ms) để quét và chuyển các đơn quá hạn quyết toán 24h sang payment_overdue
    @Scheduled(fixedDelay = 300000, initialDelay = 15000)
    public void scheduleMarkOverdueSettlements() {
        try {
            int marked = bookingService.markOverdueSettlements();
            if (marked > 0) {
                log.info("SettlementExpiryScheduler: Đã tự động chuyển {} đơn hàng quá hạn 24h sang payment_overdue.", marked);
            }
        } catch (Exception ex) {
            log.error("SettlementExpiryScheduler gặp lỗi khi quét đơn quá hạn: {}", ex.getMessage());
        }
    }
}
