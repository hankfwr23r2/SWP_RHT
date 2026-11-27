package com.example.racehorse_transport.util;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Nhận biết cơ sở dữ liệu nằm trên máy cá nhân, để chỉ bật các chế độ tiện cho máy dev ở đó
public final class LocalDatabase {

    private static final Pattern HOST = Pattern.compile("^jdbc:[a-z]+://([^:/?,]+)(?::\\d+)?/.*");
    // Chạy trên máy dùng localhost, docker compose dùng tên dịch vụ postgres
    private static final List<String> LOCAL_HOSTS = List.of("localhost", "127.0.0.1", "postgres");

    private LocalDatabase() {
    }

    public static boolean isLocal(String datasourceUrl) {
        if (datasourceUrl == null) {
            return false;
        }
        Matcher host = HOST.matcher(datasourceUrl);
        return host.matches() && LOCAL_HOSTS.contains(host.group(1));
    }
}
