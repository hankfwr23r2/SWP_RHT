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

    // Quét thường xuyên để nhả phân công sau hạn cọc/khoảng chờ xác nhận.
    @Scheduled(fixedDelayString = "${vnpay.quotation-expiry-scan-ms:30000}", initialDelay = 10000)
    public void scheduleReleaseExpiredQuotations() {
        try {
            int released = bookingService.releaseExpiredQuotations();
            if (released > 0) {
                log.info("QuotationExpiryScheduler: Đã tự động giải phóng xe và kíp xe cho {} đơn hàng hết hạn.", released);
            }
        } catch (Exception ex) {
            log.error("QuotationExpiryScheduler gặp lỗi khi quét đơn hết hạn: {}", ex.getMessage());
        }
    }
}
