package com.example.racehorse_transport.service;

import com.example.racehorse_transport.repository.EmailOtpRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class ExpiredOtpCleanup {
    private final EmailOtpRepository repository;
    @Scheduled(fixedDelayString = "${app.otp.cleanup-delay-ms:3600000}", initialDelayString = "${app.otp.cleanup-delay-ms:3600000}")
    @Transactional
    public void cleanExpired() {
        // Keep expired rows briefly so cleanup cannot reset an active resend cooldown.
        repository.deleteExpired(Instant.now().minusSeconds(60));
    }
}
