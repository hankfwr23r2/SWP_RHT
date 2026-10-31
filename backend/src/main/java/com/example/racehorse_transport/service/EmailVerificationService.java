package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.EmailVerificationOtp;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.repository.EmailVerificationOtpRepository;
import com.example.racehorse_transport.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

// OTP xác thực email của khách: gửi khi đăng ký hoặc khi tài khoản chưa xác thực đăng nhập; cùng quy tắc với OTP quên mật khẩu.
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {
    private static final Duration OTP_TTL = Duration.ofMinutes(10);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final EmailVerificationOtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.password-reset.from:}")
    private String fromAddress;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Transactional
    public void sendOtp(String email) {
        String normalizedEmail = normalize(email);
        Instant now = Instant.now();
        EmailVerificationOtp otp = otpRepository.findByEmailIgnoreCase(normalizedEmail).orElseGet(EmailVerificationOtp::new);
        if (otp.getLastSentAt() != null && otp.getLastSentAt().isAfter(now.minus(RESEND_COOLDOWN))) return;

        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        if (mailHost == null || mailHost.isBlank()) {
            // Máy local chưa cấu hình mail: in mã ra log để vẫn thử được luồng đăng ký
            log.warn("Chưa cấu hình mail, OTP xác thực email {} là {}", normalizedEmail, code);
        } else {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                if (fromAddress != null && !fromAddress.isBlank()) message.setFrom(fromAddress);
                message.setTo(normalizedEmail);
                message.setSubject("Mã xác thực email - Vận chuyển Ngựa");
                message.setText("Mã xác thực email của bạn là: " + code
                        + "\nMã có hiệu lực trong 10 phút. Không chia sẻ mã này với bất kỳ ai.");
                mailSender.send(message);
            } catch (MailException ex) {
                log.error("Unable to send email-verification OTP");
                return;
            }
        }

        otp.setEmail(normalizedEmail);
        otp.setOtpHash(passwordEncoder.encode(code));
        otp.setExpiresAt(now.plus(OTP_TTL));
        otp.setLastSentAt(now);
        otp.setAttempts(0);
        otpRepository.save(otp);
    }

    // Đúng mã thì đánh dấu email đã xác thực và trả về tài khoản
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public User confirm(String email, String code) {
        String normalizedEmail = normalize(email);
        EmailVerificationOtp otp = otpRepository.findByEmailIgnoreCase(normalizedEmail).orElseThrow(this::invalidOtp);
        if (!otp.getExpiresAt().isAfter(Instant.now()) || otp.getAttempts() >= MAX_ATTEMPTS) {
            otpRepository.delete(otp);
            throw invalidOtp();
        }
        if (code == null || !passwordEncoder.matches(code, otp.getOtpHash())) {
            otp.setAttempts(otp.getAttempts() + 1);
            if (otp.getAttempts() >= MAX_ATTEMPTS) otpRepository.delete(otp);
            else otpRepository.save(otp);
            throw invalidOtp();
        }

        User user = userRepository.findByEmailIgnoreCase(normalizedEmail).orElseThrow(this::invalidOtp);
        user.setEmailVerified(true);
        otpRepository.delete(otp);
        return userRepository.save(user);
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private IllegalArgumentException invalidOtp() {
        return new IllegalArgumentException("OTP không hợp lệ hoặc đã hết hạn. Hãy yêu cầu mã mới.");
    }
}
