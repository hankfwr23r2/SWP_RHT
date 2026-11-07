package com.example.racehorse_transport.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

// Gửi email (OTP, hóa đơn). Có BREVO_API_KEY thì gửi qua API HTTPS của Brevo (Render gói miễn phí chặn cổng SMTP 25/465/587);
// không có thì gửi qua SMTP (MAIL_HOST), như khi chạy local với Gmail. Lỗi gửi ném MailException kèm lý do.
@Component
public class OtpMailSender {
    private static final String SENDER_NAME = "Vận chuyển Ngựa";

    private final JavaMailSender smtp;
    private final String brevoApiKey;
    private final String brevoBaseUrl;
    private final String smtpHost;
    private final String fromAddress;

    public OtpMailSender(
            JavaMailSender smtp,
            @Value("${app.mail.brevo.api-key:}") String brevoApiKey,
            @Value("${app.mail.brevo.base-url:https://api.brevo.com/v3}") String brevoBaseUrl,
            @Value("${spring.mail.host:}") String smtpHost,
            @Value("${app.password-reset.from:}") String fromAddress) {
        this.smtp = smtp;
        this.brevoApiKey = brevoApiKey == null ? "" : brevoApiKey.trim();
        this.brevoBaseUrl = brevoBaseUrl;
        this.smtpHost = smtpHost == null ? "" : smtpHost.trim();
        this.fromAddress = fromAddress == null ? "" : fromAddress.trim();
    }

    // Chưa cấu hình cách gửi nào: nơi gọi tự quyết (vd. in OTP ra log khi chạy local)
    public boolean isConfigured() {
        return !brevoApiKey.isEmpty() || !smtpHost.isEmpty();
    }

    public void send(String to, String subject, String text) {
        if (!brevoApiKey.isEmpty()) sendWithBrevo(to, subject, text, null);
        else sendWithSmtp(to, subject, text);
    }

    // Thư HTML kèm bản chữ thuần cho trình đọc thư không hiện HTML (dùng cho mail hóa đơn)
    public void sendHtml(String to, String subject, String html, String text) {
        if (!brevoApiKey.isEmpty()) sendWithBrevo(to, subject, text, html);
        else sendHtmlWithSmtp(to, subject, html, text);
    }

    private void sendWithBrevo(String to, String subject, String text, String html) {
        if (fromAddress.isEmpty()) {
            throw new MailSendException("Brevo cần địa chỉ gửi đã xác minh: đặt PASSWORD_RESET_FROM hoặc MAIL_USERNAME");
        }
        JdkClientHttpRequestFactory timeouts = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        timeouts.setReadTimeout(Duration.ofSeconds(10));
        try {
            RestClient.builder().baseUrl(brevoBaseUrl).requestFactory(timeouts).build()
                    .post()
                    .uri("/smtp/email")
                    .header("api-key", brevoApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(brevoBody(to, subject, text, html))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw new MailSendException("Brevo từ chối gửi (" + ex.getStatusCode().value() + "): " + ex.getResponseBodyAsString(), ex);
        } catch (RestClientException ex) {
            throw new MailSendException("Không gọi được Brevo: " + ex.getMessage(), ex);
        }
    }

    private Map<String, Object> brevoBody(String to, String subject, String text, String html) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("sender", Map.of("name", SENDER_NAME, "email", fromAddress));
        body.put("to", List.of(Map.of("email", to)));
        body.put("subject", subject);
        body.put("textContent", text);
        if (html != null) body.put("htmlContent", html);
        return body;
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
