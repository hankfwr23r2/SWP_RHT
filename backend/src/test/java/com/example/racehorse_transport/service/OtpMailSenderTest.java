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
    void sendsHtmlBillThroughBrevoWithAPlainTextFallback() {
        brevoSender().sendHtml("khach@gmail.com", "Báo giá đơn BK-033", "<h2>Báo giá</h2><p>15,420,000 ₫</p>", "Tổng cộng: 15,420,000 ₫");

        assertThat(body.get())
                .contains("\"htmlContent\":\"<h2>Báo giá</h2><p>15,420,000 ₫</p>\"")
                .contains("\"textContent\":\"Tổng cộng: 15,420,000 ₫\"");
    }

    // Máy chủ SMTP giả ngay trong test: nhận một thư và giữ lại nội dung DATA
    @Test
    void sendsHtmlBillOverSmtpAsHtmlWithPlainTextAlternative() throws Exception {
        try (java.net.ServerSocket server = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            AtomicReference<String> data = new AtomicReference<>();
            Thread smtpThread = new Thread(() -> {
                try (java.net.Socket socket = server.accept();
                     var in = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                     var out = new java.io.PrintWriter(new java.io.OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
                    out.print("220 localhost\r\n"); out.flush();
                    String line;
                    while ((line = in.readLine()) != null) {
                        String cmd = line.toUpperCase();
                        if (cmd.startsWith("EHLO") || cmd.startsWith("HELO")) { out.print("250 localhost\r\n"); }
                        else if (cmd.startsWith("DATA")) {
                            out.print("354 go\r\n"); out.flush();
                            StringBuilder message = new StringBuilder();
                            while (!(line = in.readLine()).equals(".")) message.append(line).append('\n');
                            data.set(message.toString());
                            out.print("250 queued\r\n");
                        } else if (cmd.startsWith("QUIT")) { out.print("221 bye\r\n"); out.flush(); break; }
                        else out.print("250 OK\r\n");
                        out.flush();
                    }
                } catch (IOException ignored) {
                }
            });
            smtpThread.start();
            var smtp = new org.springframework.mail.javamail.JavaMailSenderImpl();
            smtp.setHost("127.0.0.1");
            smtp.setPort(server.getLocalPort());
            OtpMailSender sender = new OtpMailSender(smtp, "", "https://api.brevo.com/v3", "127.0.0.1", "nhaxe@gmail.com");

            sender.sendHtml("khach@gmail.com", "Báo giá đơn BK-033", "<h2>Báo giá</h2>", "Tổng cộng: 15,420,000 đ");
            smtpThread.join(5000);

            assertThat(data.get()).contains("multipart/alternative").contains("text/html").contains("text/plain")
                    .contains("To: khach@gmail.com").contains("Subject: =?UTF-8?");
        }
    }

    @Test
    void isConfiguredWhenEitherBrevoOrSmtpIsSet() {
        assertThat(new OtpMailSender(null, "", "https://api.brevo.com/v3", "", "").isConfigured()).isFalse();
        assertThat(new OtpMailSender(null, "key", "https://api.brevo.com/v3", "", "").isConfigured()).isTrue();
        assertThat(new OtpMailSender(null, "", "https://api.brevo.com/v3", "smtp.gmail.com", "").isConfigured()).isTrue();
    }
}
