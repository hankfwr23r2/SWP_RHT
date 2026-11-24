package com.example.racehorse_transport.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.config.LocalDemoProperties;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockMultipartFile;

class LocalFileClientTest {

    @TempDir Path directory;
    private LocalFileClient client;

    @BeforeEach
    void setUp() {
        client = new LocalFileClient(new LocalDemoProperties(directory.toString(),
                "jdbc:postgresql://localhost:5432/db", new MockEnvironment()));
    }

    @Test
    void keepsTheFileUntilItIsDeleted() throws Exception {
        String url = client.uploadFile(new MockMultipartFile("file", "Bien Ban.PDF", "application/pdf", new byte[]{1, 2}), "documents");
        String name = url.substring(url.lastIndexOf('/') + 1);
        assertThat(url).startsWith("/api/v1/public/demo/files/").endsWith(".pdf");
        assertThat(client.read(name).getContentAsByteArray()).containsExactly(1, 2);
        client.deleteFile(url);
        assertThatThrownBy(() -> client.read(name)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void neverReadsOutsideItsFolder() {
        assertThatThrownBy(() -> client.read("../clock-offset.txt")).isInstanceOf(ResourceNotFoundException.class);
        client.deleteFile("/api/v1/public/demo/files/../clock-offset.txt");
        client.deleteFile("https://example.com/a.pdf");
    }
}
