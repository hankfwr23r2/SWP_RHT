package com.example.racehorse_transport.config;

import com.example.racehorse_transport.util.LocalDatabase;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

// Khóa ký JWT. Bản deploy bắt buộc đặt APP_JWT_SECRET; thiếu khóa hoặc khóa yếu thì không khởi động.
// Máy dev (DB trên máy, không có profile prod) chưa đặt khóa thì dùng khóa ngẫu nhiên, khởi động lại là phải đăng nhập lại.
@Slf4j
@Component
public class JwtProperties {

    // HS256 cần khóa tối thiểu 256 bit
    static final int MIN_SECRET_BYTES = 32;
    // Khóa mặc định của bản cũ đã nằm trong lịch sử Git, coi như đã lộ
    static final String LEAKED_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    private final byte[] signingKey;

    public JwtProperties(@Value("${app.jwt.secret:}") String secret,
                         @Value("${spring.datasource.url:}") String datasourceUrl, Environment environment) {
        this.signingKey = resolve(secret == null ? "" : secret.trim(), datasourceUrl, environment);
    }

    public byte[] signingKey() {
        return signingKey.clone();
    }

    private static byte[] resolve(String secret, String datasourceUrl, Environment environment) {
        if (LEAKED_SECRET.equalsIgnoreCase(secret)) {
            throw new IllegalStateException("APP_JWT_SECRET đang là khóa mặc định cũ đã lộ. Hãy tạo khóa mới.");
        }
        if (!secret.isEmpty()) {
            byte[] key = secret.getBytes(StandardCharsets.UTF_8);
            if (key.length < MIN_SECRET_BYTES) {
                throw new IllegalStateException("APP_JWT_SECRET phải dài ít nhất " + MIN_SECRET_BYTES + " byte.");
            }
            return key;
        }
        boolean production = environment.acceptsProfiles(Profiles.of("prod", "production"));
        if (production || !LocalDatabase.isLocal(datasourceUrl)) {
            throw new IllegalStateException("Chưa đặt APP_JWT_SECRET. Bản deploy phải có khóa ký JWT riêng.");
        }
        log.warn("Chưa đặt APP_JWT_SECRET: dùng khóa ngẫu nhiên cho máy dev, khởi động lại sẽ phải đăng nhập lại.");
        byte[] key = new byte[64];
        new SecureRandom().nextBytes(key);
        return key;
    }
}
