package com.example.racehorse_transport.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

// CORS khai báo một nơi, Spring Security dùng bean này (SecurityConfig: .cors(...)).
// Origin lấy từ app.cors.allowed-origins (biến môi trường APP_CORS_ALLOWED_ORIGINS), không mở theo đuôi tên miền dùng chung.
@Configuration
public class CorsConfig {

    // Cho trình duyệt nhớ kết quả kiểm tra CORS (preflight) 2 giờ; không đặt thì trình duyệt chỉ nhớ ~5 giây
    // và gửi thêm một request OPTIONS trước gần như mọi lần gọi API
    private static final long PREFLIGHT_CACHE_SECONDS = 7200;

    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(parseOrigins(allowedOrigins));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(PREFLIGHT_CACHE_SECONDS);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    static List<String> parseOrigins(String allowedOrigins) {
        List<String> origins = Arrays.stream(allowedOrigins == null ? new String[0] : allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        if (origins.isEmpty() || origins.contains("*")) {
            throw new IllegalStateException("APP_CORS_ALLOWED_ORIGINS phải liệt kê đúng tên miền frontend, không được để trống hay dùng *.");
        }
        return origins;
    }
}
