package com.example.racehorse_transport.scheduler;

import com.example.racehorse_transport.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuotationExpiryScheduler {

    private final BookingService bookingService;

    // Chạy định kỳ mỗi 5 phút (300.000 ms) để quét và giải phóng xe/kíp xe của các đơn quá hạn 48h
    @Scheduled(fixedDelay = 300000, initialDelay = 10000)
    public void scheduleReleaseExpiredQuotations() {
        try {
            int released = bookingService.releaseExpiredQuotations();
            if (released > 0) {
                log.info("QuotationExpiryScheduler: Đã tự động giải phóng xe và kíp xe cho {} đơn hàng hết hạn 48h.", released);
            }
        } catch (Exception ex) {
            log.error("QuotationExpiryScheduler gặp lỗi khi quét đơn hết hạn: {}", ex.getMessage());
        }
    }
}
