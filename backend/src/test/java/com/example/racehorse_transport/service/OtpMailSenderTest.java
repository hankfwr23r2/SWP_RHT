package com.example.racehorse_transport.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Gửi OTP qua API HTTPS của Brevo (Render gói miễn phí chặn cổng SMTP). Máy chủ HTTP thật chạy trong test đóng vai Brevo.
class OtpMailSenderTest {

    private HttpServer brevo;
    private final AtomicReference<String> method = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> apiKey = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private volatile int status = 201;
    private volatile String reply = "{\"messageId\":\"<abc@smtp-relay.mailin.fr>\"}";

    @BeforeEach
    void startFakeBrevo() throws IOException {
        brevo = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        brevo.createContext("/", exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            apiKey.set(exchange.getRequestHeaders().getFirst("api-key"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = reply.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, out.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(out); }
        });
        brevo.start();
    }

    @AfterEach
    void stopFakeBrevo() {
        brevo.stop(0);
    }

    private OtpMailSender brevoSender() {
        String baseUrl = "http://127.0.0.1:" + brevo.getAddress().getPort() + "/v3";
        return new OtpMailSender(null, "brevo-key-123", baseUrl, "", "nhaxe@gmail.com");
    }

    @Test
    void sendsOtpThroughBrevoApiWhenApiKeyIsSet() {
        brevoSender().send("khach@gmail.com", "Mã xác thực email - Vận chuyển Ngựa", "Mã xác thực email của bạn là: 123456");

        assertThat(method.get()).isEqualTo("POST");
        assertThat(path.get()).isEqualTo("/v3/smtp/email");
        assertThat(apiKey.get()).isEqualTo("brevo-key-123");
        assertThat(body.get())
                .contains("\"email\":\"nhaxe@gmail.com\"")
                .contains("\"to\":[{\"email\":\"khach@gmail.com\"}]")
                .contains("\"subject\":\"Mã xác thực email - Vận chuyển Ngựa\"")
                .contains("\"textContent\":\"Mã xác thực email của bạn là: 123456\"");
    }

    @Test
    void reportsBrevoRejectionAsMailErrorWithReason() {
        status = 401;
        reply = "{\"code\":\"unauthorized\",\"message\":\"Key not found\"}";

        assertThatThrownBy(() -> brevoSender().send("khach@gmail.com", "Tiêu đề", "Nội dung"))
                .isInstanceOf(MailException.class)
                .hasMessageContaining("401")
                .hasMessageContaining("Key not found");
    }

    @Test
    void reportsMissingSenderAddressBeforeCallingBrevo() {
        String baseUrl = "http://127.0.0.1:" + brevo.getAddress().getPort() + "/v3";
        OtpMailSender sender = new OtpMailSender(null, "brevo-key-123", baseUrl, "", "");

        assertThatThrownBy(() -> sender.send("khach@gmail.com", "Tiêu đề", "Nội dung"))
                .isInstanceOf(MailException.class)
                .hasMessageContaining("địa chỉ gửi");
        assertThat(method.get()).isNull();
    }

    @Test
    void isConfiguredWhenEitherBrevoOrSmtpIsSet() {
        assertThat(new OtpMailSender(null, "", "https://api.brevo.com/v3", "", "").isConfigured()).isFalse();
        assertThat(new OtpMailSender(null, "key", "https://api.brevo.com/v3", "", "").isConfigured()).isTrue();
        assertThat(new OtpMailSender(null, "", "https://api.brevo.com/v3", "smtp.gmail.com", "").isConfigured()).isTrue();
    }
}
