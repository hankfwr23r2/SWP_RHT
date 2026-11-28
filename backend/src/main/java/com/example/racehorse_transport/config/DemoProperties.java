package com.example.racehorse_transport.config;

import com.example.racehorse_transport.util.LocalDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

// Chế độ demo trên giờ thật (profile demo-live, docs/CHAY_THU.md): bỏ qua các mốc thời gian chặn thao tác
// (chốt nhận đơn, quyết định chạy 48 giờ, thời gian làm giấy, giờ chờ, giờ nghỉ) để đi hết một đơn trong buổi demo,
// trong khi VNPAY, Cloudinary, mail, Turnstile vẫn chạy thật. Chỉ bật được với cơ sở dữ liệu trên máy cá nhân.
@Component
public class DemoProperties {

    private final boolean skipTimeRules;

    public DemoProperties(@Value("${app.demo.skip-time-rules:false}") boolean skipTimeRules,
                          @Value("${spring.datasource.url:}") String datasourceUrl, Environment environment) {
        if (skipTimeRules && (environment.acceptsProfiles(Profiles.of("prod", "production"))
                || !LocalDatabase.isLocal(datasourceUrl))) {
            throw new IllegalStateException("app.demo.skip-time-rules chỉ bật được với cơ sở dữ liệu trên máy cá nhân.");
        }
        this.skipTimeRules = skipTimeRules;
    }

    public boolean skipTimeRules() {
        return skipTimeRules;
    }
}
