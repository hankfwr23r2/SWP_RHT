package com.example.racehorse_transport.scheduler;

import com.example.racehorse_transport.service.DeliveryFailureService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Kiểm các lần chờ người nhận đã quá giờ chờ miễn phí (mặc định mỗi 5 phút), đánh dấu giao thất bại (B5)
@Component
@RequiredArgsConstructor
public class DeliveryTimeoutScheduler {

    private final DeliveryFailureService deliveryFailures;

    @Scheduled(fixedDelayString = "${app.delivery.timeout-delay-ms:300000}", initialDelayString = "${app.delivery.timeout-delay-ms:300000}")
    public void timeoutExpired() {
        deliveryFailures.timeoutExpired();
    }
}
