package com.example.racehorse_transport.config;

import com.example.racehorse_transport.util.OffsetClock;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("local-demo")
public class LocalDemoConfig {

    // Khoảng tua được giữ qua lần khởi động lại, để dữ liệu đã ghi không nằm ở tương lai
    @Bean
    @Primary
    public OffsetClock offsetClock(LocalDemoProperties properties) throws IOException {
        Duration offset = Files.exists(properties.clockFile())
                ? Duration.parse(Files.readString(properties.clockFile()).trim()) : Duration.ZERO;
        return new OffsetClock(Clock.systemUTC(), offset);
    }

    // Cổng thanh toán giả lập: trang trả tiền nằm ngay trong backend, khóa ký chỉ dùng trong chế độ này
    @Bean
    @Primary
    public VnpayProperties localDemoVnpayProperties() {
        return new VnpayProperties() {
            @Override
            public String getTmnCode() {
                return "LOCALDEMO";
            }

            @Override
            public String getHashSecret() {
                return "local-demo-signing";
            }

            @Override
            public String getPayUrl() {
                return "/api/v1/public/demo/vnpay";
            }

            @Override
            public String getReturnUrl() {
                return "/payment/vnpay-return";
            }

            @Override
            public boolean isConfigured() {
                return true;
            }
        };
    }
}
