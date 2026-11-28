package com.example.racehorse_transport.config;

import com.example.racehorse_transport.util.LocalDatabase;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

// Chế độ chạy thử trên máy cá nhân: tua giờ, tệp lưu tại máy. Không khởi động được với DB ngoài máy.
@Component
@Profile("local-demo")
public class LocalDemoProperties {

    private final Path directory;

    public LocalDemoProperties(@Value("${app.local-demo.directory:local-demo}") String directory,
                               @Value("${spring.datasource.url:}") String datasourceUrl, Environment environment) {
        if (environment.acceptsProfiles(Profiles.of("prod", "production")) || !LocalDatabase.isLocal(datasourceUrl)) {
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
