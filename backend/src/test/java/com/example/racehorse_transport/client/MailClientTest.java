package com.example.racehorse_transport.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MailClientTest {

    @Test
    void skipsAddressesOnDomainsReservedForTesting() {
        // smtp null: nếu có gửi qua SMTP thì test lỗi ngay
        MailClient smtpMail = new MailClient(null, "smtp.gmail.com", "nhaxe@gmail.com");

        for (String to : java.util.List.of("customer1.dev@racehorse.test", "a@shop.example", "b@x.invalid",
                "c@localhost", "d@example.com", "e@mail.example.org", "F@EXAMPLE.NET")) {
            smtpMail.send(to, "Mã xác thực", "Mã 123456");
            smtpMail.sendHtml(to, "Hóa đơn", "<p>Hóa đơn</p>", "Hóa đơn");
        }
    }

    @Test
    void stillSendsToRealDomainsThatMerelyContainReservedWords() throws Exception {
        String data = sendThroughFakeSmtp(sender -> sender.send("khach@example-horse.vn", "Mã xác thực", "Mã 123456"));

        assertThat(data).contains("To: khach@example-horse.vn");
    }

    @Test
    void sendsHtmlBillOverSmtpAsHtmlWithPlainTextAlternative() throws Exception {
        String data = sendThroughFakeSmtp(sender ->
                sender.sendHtml("khach@gmail.com", "Báo giá đơn BK-033", "<h2>Báo giá</h2>", "Tổng cộng: 15,420,000 đ"));

        assertThat(data).contains("multipart/alternative").contains("text/html").contains("text/plain")
                .contains("To: khach@gmail.com").contains("Subject: =?UTF-8?");
    }

    @Test
    void isConfiguredWhenSmtpHostIsSet() {
        assertThat(new MailClient(null, "", "").isConfigured()).isFalse();
        assertThat(new MailClient(null, "smtp.gmail.com", "").isConfigured()).isTrue();
    }

    // Máy chủ SMTP giả ngay trong test: nhận một thư và trả lại nội dung DATA
    private static String sendThroughFakeSmtp(java.util.function.Consumer<MailClient> send) throws Exception {
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

            send.accept(new MailClient(smtp, "127.0.0.1", "nhaxe@gmail.com"));
            smtpThread.join(5000);
            return data.get();
        }
    }
}
