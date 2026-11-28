package com.example.racehorse_transport.client;

import com.example.racehorse_transport.config.LocalDemoProperties;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

// Giữ tệp trên máy thay cho Cloudinary ở chế độ chạy thử
@Service
@Primary
@Profile("local-demo")
public class LocalFileClient extends CloudinaryClient {

    private static final String URL_PREFIX = "/api/v1/public/demo/files/";
    private static final Pattern EXTENSION = Pattern.compile("\\.(png|jpe?g|webp|pdf)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern STORED_NAME = Pattern.compile("[0-9a-f-]{36}(\\.[a-z]{3,4})?");

    private final Path directory;

    public LocalFileClient(LocalDemoProperties properties) {
        super(null);
        this.directory = properties.filesDirectory();
    }

    @Override
    public String uploadFile(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File tải lên không được để trống!");
        }
        Matcher extension = EXTENSION.matcher(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        String name = UUID.randomUUID() + (extension.find() ? extension.group().toLowerCase(Locale.ROOT) : "");
        try {
            Files.createDirectories(directory);
            Files.write(directory.resolve(name), file.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return URL_PREFIX + name;
    }

    @Override
    public void deleteFile(String fileReference) {
        if (fileReference == null || !fileReference.startsWith(URL_PREFIX)) {
            return;
        }
        String name = fileReference.substring(URL_PREFIX.length());
        if (!STORED_NAME.matcher(name).matches()) {
            return;
        }
        try {
            Files.deleteIfExists(directory.resolve(name));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Resource read(String name) {
        if (!STORED_NAME.matcher(name).matches() || !Files.isRegularFile(directory.resolve(name))) {
            throw new ResourceNotFoundException("Không tìm thấy tệp.");
        }
        return new FileSystemResource(directory.resolve(name));
    }
}
