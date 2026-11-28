package com.example.racehorse_transport.security;

import java.util.Arrays;
import java.util.Optional;

// Hai ứng dụng frontend. Mỗi ứng dụng giữ refresh token trong cookie riêng, để đăng nhập ứng dụng này
// không ghi đè phiên của ứng dụng kia trên cùng trình duyệt.
public enum ClientApp {
    CUSTOMER("customer", "rt_customer"),
    BACKOFFICE("backoffice", "rt_staff");

    // Frontend gửi header này khi làm mới token và đăng xuất
    public static final String HEADER = "X-Client-App";

    private final String code;
    private final String cookieName;

    ClientApp(String code, String cookieName) {
        this.code = code;
        this.cookieName = cookieName;
    }

    public String cookieName() {
        return cookieName;
    }

    // Ứng dụng của tài khoản theo vai trò: khách dùng trang khách, mọi vai trò khác dùng backoffice
    public static ClientApp forRole(String role) {
        return "CUSTOMER".equals(role) ? CUSTOMER : BACKOFFICE;
    }

    public static Optional<ClientApp> fromHeader(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim();
        return Arrays.stream(values()).filter(app -> app.code.equalsIgnoreCase(normalized)).findFirst();
    }
}
