package com.example.racehorse_transport.scheduler;

import com.example.racehorse_transport.service.QuotationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class QuotationExpiryScheduler {
    private final QuotationService quotations;

    @Scheduled(fixedDelayString = "${quotation.expiry-scan-interval-ms:60000}",
            initialDelayString = "${quotation.expiry-scan-interval-ms:60000}")
    public void expireQuotations() {
        for (Long id : quotations.expirationCandidates()) {
            try {
                quotations.expire(id);
            } catch (RuntimeException ex) {
                log.error("Không thể xử lý báo giá hết hạn {}", id, ex);
            }
        }
    }
}
