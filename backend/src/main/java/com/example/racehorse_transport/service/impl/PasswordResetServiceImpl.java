package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.ResetPasswordWithOtpRequest;
import com.example.racehorse_transport.entity.PasswordResetOtp;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.repository.PasswordResetOtpRepository;
import com.example.racehorse_transport.repository.UserRepository;
import com.example.racehorse_transport.service.OtpMailSender;
import com.example.racehorse_transport.service.PasswordResetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {
    private static final Duration OTP_TTL = Duration.ofMinutes(10);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final PasswordResetOtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpMailSender mailSender;
    private final SecureRandom secureRandom = new SecureRandom();


    @Override
    @Transactional
    public void requestOtp(String email) {
        String normalizedEmail = normalize(email);
        var user = userRepository.findByEmailIgnoreCase(normalizedEmail);
        if (user.isEmpty()) return;

        Instant now = Instant.now();
        PasswordResetOtp otp = otpRepository.findByEmailIgnoreCase(normalizedEmail).orElseGet(PasswordResetOtp::new);
        if (otp.getLastSentAt() != null && otp.getLastSentAt().isAfter(now.minus(RESEND_COOLDOWN))) return;

        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        try {
            mailSender.send(user.get().getEmail(), "Mã OTP đặt lại mật khẩu - Vận chuyển Ngựa",
                    "Mã OTP đặt lại mật khẩu của bạn là: " + code
                    + "\nMã có hiệu lực trong 10 phút. Không chia sẻ mã này với bất kỳ ai."
                    + "\nNếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này.");
        } catch (MailException ex) {
            log.error("Unable to send password-reset OTP email: {}", ex.getMessage());
            return;
        }

        otp.setEmail(normalizedEmail);
        otp.setOtpHash(passwordEncoder.encode(code));
        otp.setExpiresAt(now.plus(OTP_TTL));
        otp.setLastSentAt(now);
        otp.setAttempts(0);
        otpRepository.save(otp);
    }

    @Override
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public void resetPassword(ResetPasswordWithOtpRequest request) {
        String normalizedEmail = normalize(request.getEmail());
        PasswordResetOtp otp = otpRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(this::invalidOtp);
        Instant now = Instant.now();
        if (!otp.getExpiresAt().isAfter(now) || otp.getAttempts() >= MAX_ATTEMPTS) {
            otpRepository.delete(otp);
            throw invalidOtp();
        }

        if (!passwordEncoder.matches(request.getOtp(), otp.getOtpHash())) {
            otp.setAttempts(otp.getAttempts() + 1);
            if (otp.getAttempts() >= MAX_ATTEMPTS) otpRepository.delete(otp);
            else otpRepository.save(otp);
            throw invalidOtp();
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Mật khẩu xác nhận không khớp.");
        }

        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(this::invalidOtp);
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Mật khẩu mới phải khác mật khẩu hiện tại.");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        // Nhận được OTP qua email cũng là đã xác thực email
        user.setEmailVerified(true);
        userRepository.save(user);
        otpRepository.delete(otp);
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private IllegalArgumentException invalidOtp() {
        return new IllegalArgumentException("OTP không hợp lệ hoặc đã hết hạn. Hãy yêu cầu mã mới.");
    }
}
