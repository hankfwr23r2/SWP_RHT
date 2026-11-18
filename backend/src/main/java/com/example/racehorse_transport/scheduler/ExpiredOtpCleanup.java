package com.example.racehorse_transport.scheduler;

import com.example.racehorse_transport.service.EmailVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExpiredOtpCleanup {
    private final EmailVerificationService emailVerificationService;

    @Scheduled(fixedDelayString = "${app.otp.cleanup-delay-ms:3600000}", initialDelayString = "${app.otp.cleanup-delay-ms:3600000}")
    public void cleanExpired() {
        emailVerificationService.cleanExpiredOtps();
    }
}
