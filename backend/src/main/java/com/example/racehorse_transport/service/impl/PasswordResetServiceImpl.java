package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.client.MailClient;
import com.example.racehorse_transport.dto.auth.ResetPasswordWithOtpRequestDto;
import com.example.racehorse_transport.entity.AppUser;
import com.example.racehorse_transport.entity.Otp;
import com.example.racehorse_transport.entity.enums.OtpPurpose;
import com.example.racehorse_transport.policy.OtpPolicy;
import com.example.racehorse_transport.repository.AppUserRepository;
import com.example.racehorse_transport.repository.OtpRepository;
import com.example.racehorse_transport.service.PasswordResetService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {

    private final AppUserRepository users;
    private final OtpRepository otps;
    private final PasswordEncoder passwordEncoder;
    private final MailClient mail;
    private final SystemConfigService config;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Override
    @Transactional
    public void requestOtp(String email) {
        String normalized = OtpPolicy.normalizeEmail(email);
        otps.lockEmail(normalized);
        AppUser user = users.findByEmailIgnoreCase(normalized).orElse(null);
        if (user == null) {
            return;
        }
        Instant now = clock.instant();
        Otp otp = otps.findByEmailAndPurpose(normalized, OtpPurpose.RESET_PASSWORD).orElseGet(Otp::new);
        if (!OtpPolicy.canSendAgain(otp.getLastSentAt(), now)) {
            return;
        }

        int expiryMinutes = config.getInt("OTP_EXPIRY_MINUTES");
        String code = OtpPolicy.generateCode(random);
        if (!deliver(user.getEmail(), code, expiryMinutes)) {
            return;
        }

        otp.setEmail(normalized);
        otp.setUser(user);
        otp.setPurpose(OtpPurpose.RESET_PASSWORD);
        otp.setOtpHash(passwordEncoder.encode(code));
        otp.setExpiresAt(OtpPolicy.expiryOf(now, expiryMinutes));
        otp.setLastSentAt(now);
        otp.setAttempts(0);
        otps.save(otp);
    }

    @Override
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public void resetPassword(ResetPasswordWithOtpRequestDto request) {
        String normalized = OtpPolicy.normalizeEmail(request.getEmail());
        otps.lockEmail(normalized);
        Otp otp = otps.findByEmailAndPurpose(normalized, OtpPurpose.RESET_PASSWORD).orElseThrow(this::invalidOtp);
        int maxAttempts = config.getInt("OTP_MAX_ATTEMPTS");
        if (!OtpPolicy.isUsable(otp.getExpiresAt(), otp.getAttempts(), maxAttempts, clock.instant())) {
            otps.delete(otp);
            throw invalidOtp();
        }
        if (!passwordEncoder.matches(request.getOtp(), otp.getOtpHash())) {
            otp.setAttempts(otp.getAttempts() + 1);
            if (OtpPolicy.isExhausted(otp.getAttempts(), maxAttempts)) {
                otps.delete(otp);
            } else {
                otps.save(otp);
            }
            throw invalidOtp();
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Mật khẩu xác nhận không khớp.");
        }

        AppUser user = users.findByEmailIgnoreCase(normalized).orElseThrow(this::invalidOtp);
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Mật khẩu mới phải khác mật khẩu hiện tại.");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        // Nhận được OTP qua email cũng là đã xác thực email
        user.setEmailVerified(true);
        users.save(user);
        otps.delete(otp);
    }

    // Gửi mã qua mail. Không ghi mã OTP ra log (AGENTS.md mục 9).
    private boolean deliver(String email, String code, int expiryMinutes) {
        if (!mail.isConfigured()) {
            log.warn("Chưa cấu hình mail nên không gửi được mã đặt lại mật khẩu cho {}", email);
            return false;
        }
        try {
            mail.send(email, "Mã OTP đặt lại mật khẩu - Vận chuyển Ngựa",
                    "Mã OTP đặt lại mật khẩu của bạn là: " + code
                            + "\nMã có hiệu lực trong " + expiryMinutes + " phút. Không chia sẻ mã này với bất kỳ ai."
                            + "\nNếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này.");
            return true;
        } catch (MailException ex) {
            log.error("Không gửi được mã đặt lại mật khẩu: {}", ex.getMessage());
            return false;
        }
    }

    private IllegalArgumentException invalidOtp() {
        return new IllegalArgumentException("OTP không hợp lệ hoặc đã hết hạn. Hãy yêu cầu mã mới.");
    }
}
