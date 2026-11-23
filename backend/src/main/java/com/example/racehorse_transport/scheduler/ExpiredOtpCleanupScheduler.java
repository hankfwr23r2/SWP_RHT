package com.example.racehorse_transport.scheduler;

import com.example.racehorse_transport.service.EmailVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Dọn mã OTP đã hết hạn, mặc định mỗi giờ một lần.
@Component
@RequiredArgsConstructor
public class ExpiredOtpCleanupScheduler {

    private final EmailVerificationService emailVerification;

    @Scheduled(fixedDelayString = "${app.otp.cleanup-delay-ms:3600000}", initialDelayString = "${app.otp.cleanup-delay-ms:3600000}")
    public void deleteExpiredOtps() {
        emailVerification.deleteExpiredOtps();
    }
}
