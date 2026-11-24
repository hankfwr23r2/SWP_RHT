package com.example.racehorse_transport.config;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

// Chế độ chạy thử trên máy cá nhân: tua giờ, tệp lưu tại máy, thanh toán giả lập. Không khởi động được với DB ngoài máy.
@Component
@Profile("local-demo")
public class LocalDemoProperties {

    private static final Pattern HOST = Pattern.compile("^jdbc:[a-z]+://([^:/?,]+)(?::\\d+)?/.*");
    // Chạy trên máy dùng localhost, docker compose dùng tên dịch vụ postgres
    private static final List<String> LOCAL_HOSTS = List.of("localhost", "127.0.0.1", "postgres");

    private final Path directory;

    public LocalDemoProperties(@Value("${app.local-demo.directory:local-demo}") String directory,
                               @Value("${spring.datasource.url:}") String datasourceUrl, Environment environment) {
        Matcher host = HOST.matcher(datasourceUrl);
        if (environment.acceptsProfiles(Profiles.of("prod", "production"))
                || !host.matches() || !LOCAL_HOSTS.contains(host.group(1))) {
            throw new IllegalStateException("Chế độ local-demo chỉ chạy với cơ sở dữ liệu trên máy cá nhân.");
        }
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    public Path filesDirectory() {
        return directory.resolve("files");
    }

    public Path clockFile() {
        return directory.resolve("clock-offset.txt");
    }
}
