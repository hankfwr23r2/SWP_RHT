package com.example.racehorse_transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

// Mở app (chưa đăng nhập) chỉ cần một request để có đủ cấu hình nghiệp vụ, danh mục địa điểm và giấy tờ bắt buộc;
// dữ liệu phải đúng bằng các endpoint riêng mà trang admin đang chỉnh, để admin sửa là khách thấy.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PublicBootstrapTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void oneAnonymousRequestReturnsConfigsLocationsAndDocumentRequirements() throws Exception {
        HttpResponse<String> response = get("/public/bootstrap");
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode data = json.readTree(response.body()).path("data");

        JsonNode configs = data.path("configs");
        assertThat(configs.isArray()).isTrue();
        assertThat(configs.findValuesAsText("configKey")).contains("DEPOSIT_RATE", "MIN_LEAD_DAYS");
        assertThat(data.path("locations").size()).isPositive();
        assertThat(data.path("documentRequirements").size()).isPositive();

        assertThat(configs).isEqualTo(dataOf("/public/configs"));
        assertThat(data.path("locations")).isEqualTo(dataOf("/locations"));
        assertThat(data.path("documentRequirements")).isEqualTo(dataOf("/public/document-requirements"));
    }

    private JsonNode dataOf(String path) throws Exception {
        HttpResponse<String> response = get(path);
        assertThat(response.statusCode()).as(path).isEqualTo(200);
        return json.readTree(response.body()).path("data");
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
