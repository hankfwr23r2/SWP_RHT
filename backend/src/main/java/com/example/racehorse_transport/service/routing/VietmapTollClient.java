package com.example.racehorse_transport.service.routing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.OptionalLong;

// Phí cầu đường (VND) của xe tải trên phần đường trong Việt Nam, lấy từ Vietmap Route v4 (chỉ phủ Việt Nam).
// URL chứa key nên không bao giờ ghi URL ra log.
@Slf4j
@Component
public class VietmapTollClient {

    private final String baseUrl;
    private final String apiKey;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public VietmapTollClient(@Value("${routing.vietmap.url:https://maps.vietmap.vn/api}") String baseUrl,
                             @Value("${routing.vietmap.api-key:}") String apiKey,
                             ObjectMapper json) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.json = json;
    }

    public OptionalLong truckToll(GeoPoint from, GeoPoint to, int capacityKg, Instant departAt) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Chưa cấu hình VIETMAP_API_KEY, bỏ qua phí cầu đường.");
            return OptionalLong.empty();
        }
        String query = "apikey=" + encode(apiKey)
                + "&point=" + encode(from.lat() + "," + from.lng())
                + "&point=" + encode(to.lat() + "," + to.lng())
                + "&vehicle=truck&capacity=" + capacityKg
                + "&annotations=toll"
                + "&time=" + encode(departAt.truncatedTo(ChronoUnit.SECONDS).toString());
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/route/v4?" + query))
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Vietmap trả {}", response.statusCode());
                return OptionalLong.empty();
            }
            JsonNode root = json.readTree(response.body());
            JsonNode cost = root.path("paths").path(0).path("toll_cost");
            if (!"OK".equals(root.path("code").asText()) || !cost.isNumber()) {
                log.warn("Vietmap không trả phí cầu đường: {}", root.path("code").asText());
                return OptionalLong.empty();
            }
            return OptionalLong.of(Math.round(cost.asDouble()));
        } catch (IOException e) {
            log.warn("Không gọi được Vietmap: {}", e.getClass().getSimpleName());
            return OptionalLong.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return OptionalLong.empty();
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
