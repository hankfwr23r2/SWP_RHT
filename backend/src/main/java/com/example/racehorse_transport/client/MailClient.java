package com.example.racehorse_transport.client;

import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

// Gửi email (OTP, hóa đơn) qua SMTP (MAIL_HOST), vd Gmail. Lỗi gửi ném MailException kèm lý do.
// Địa chỉ thuộc đuôi dành cho thử nghiệm (RFC 2606, vd tài khoản mẫu @racehorse.test) không có hộp thư thật: chỉ ghi log, không gửi.
@Slf4j
@Component
public class MailClient {
    private static final String SENDER_NAME = "Vận chuyển Ngựa";
    private static final List<String> RESERVED_TLDS = List.of("test", "example", "invalid", "localhost");
    private static final List<String> RESERVED_DOMAINS = List.of("example.com", "example.net", "example.org");

    private final JavaMailSender smtp;
    private final String smtpHost;
    private final String fromAddress;

    public MailClient(
            JavaMailSender smtp,
            @Value("${spring.mail.host:}") String smtpHost,
            @Value("${app.password-reset.from:}") String fromAddress) {
        this.smtp = smtp;
        this.smtpHost = smtpHost == null ? "" : smtpHost.trim();
        this.fromAddress = fromAddress == null ? "" : fromAddress.trim();
    }

    // Chưa cấu hình cách gửi nào: nơi gọi tự quyết (vd. in OTP ra log khi chạy local)
    public boolean isConfigured() {
        return !smtpHost.isEmpty();
    }

    public void send(String to, String subject, String text) {
        if (skipReserved(to, subject, text)) return;
        sendWithSmtp(to, subject, text);
    }

    // Thư HTML kèm bản chữ thuần cho trình đọc thư không hiện HTML (dùng cho mail hóa đơn)
    public void sendHtml(String to, String subject, String html, String text) {
        if (skipReserved(to, subject, text)) return;
        sendHtmlWithSmtp(to, subject, html, text);
    }

    private static boolean skipReserved(String to, String subject, String text) {
        String domain = to == null ? "" : to.substring(to.lastIndexOf('@') + 1).trim().toLowerCase(Locale.ROOT);
        boolean reserved = RESERVED_TLDS.stream().anyMatch(tld -> domain.equals(tld) || domain.endsWith("." + tld))
                || RESERVED_DOMAINS.stream().anyMatch(name -> domain.equals(name) || domain.endsWith("." + name));
        // Nội dung in ra log để vẫn thử được OTP với tài khoản mẫu
        if (reserved) log.info("Không gửi mail tới địa chỉ thử nghiệm {}: {}\n{}", to, subject, text);
        return reserved;
    }

    private void sendHtmlWithSmtp(String to, String subject, String html, String text) {
        try {
            jakarta.mail.internet.MimeMessage message = smtp.createMimeMessage();
            org.springframework.mail.javamail.MimeMessageHelper helper =
                    new org.springframework.mail.javamail.MimeMessageHelper(message, true, "UTF-8");
            if (!fromAddress.isEmpty()) helper.setFrom(fromAddress, SENDER_NAME);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, html);
            smtp.send(message);
        } catch (jakarta.mail.MessagingException | java.io.UnsupportedEncodingException ex) {
            throw new MailSendException("Không dựng được thư: " + ex.getMessage(), ex);
        }
    }

    private void sendWithSmtp(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (!fromAddress.isEmpty()) message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        smtp.send(message);
    }
}
