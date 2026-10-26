package com.example.racehorse_transport.service.routing;

import com.example.racehorse_transport.exception.NoTruckRouteException;
import com.example.racehorse_transport.exception.RoutingUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// Gọi Valhalla (dữ liệu OpenStreetMap, phủ Việt Nam, Campuchia, Lào) lấy đường đi thật của xe tải.
// Máy chủ công khai chỉ dùng cho demo/test; chạy thật thì tự dựng và đổi routing.valhalla.url.
@Slf4j
@Component
public class ValhallaClient {

    // 442: không có đường đi; 171: không có đường xe tải nào gần điểm
    private static final Set<Integer> NO_PATH_CODES = Set.of(442, 171);

    // Máy chủ công khai yêu cầu User-Agent nêu tên ứng dụng
    static final String USER_AGENT = "racehorse-transport/1.0 (+https://github.com/hankfwr23r2/SWP_RHT)";

    private final String baseUrl;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public ValhallaClient(@Value("${routing.valhalla.url:https://valhalla1.openstreetmap.de}") String baseUrl, ObjectMapper json) {
        this.baseUrl = baseUrl;
        this.json = json;
    }

    public RoadRoute truckRoute(List<GeoPoint> points, double weightTons, int topSpeedKmh, LocalDateTime departLocal) {
        ObjectNode body = json.createObjectNode();
        ArrayNode locations = body.putArray("locations");
        points.forEach(p -> locations.addObject().put("lat", p.lat()).put("lon", p.lng()).put("type", "break"));
        body.put("costing", "truck");
        body.putObject("costing_options").putObject("truck").put("weight", weightTons).put("top_speed", topSpeedKmh);
        body.put("units", "kilometers");
        body.putObject("date_time").put("type", 1).put("value", departLocal.truncatedTo(ChronoUnit.MINUTES).toString());

        HttpResponse<String> response;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/route"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", USER_AGENT)
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            log.warn("Không gọi được Valhalla: {}", e.toString());
            throw new RoutingUnavailableException();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RoutingUnavailableException();
        }

        JsonNode root = readOrNull(response.body());
        if (response.statusCode() == 400 && root != null && NO_PATH_CODES.contains(root.path("error_code").asInt())) {
            throw new NoTruckRouteException();
        }
        if (response.statusCode() != 200 || root == null) {
            log.warn("Valhalla trả {}: {}", response.statusCode(), root == null ? "không phải JSON" : root.path("error").asText());
            throw new RoutingUnavailableException();
        }

        JsonNode trip = root.path("trip");
        List<RoadRoute.Leg> legs = new ArrayList<>();
        for (JsonNode leg : trip.path("legs")) {
            legs.add(new RoadRoute.Leg(
                    leg.path("summary").path("length").asDouble(),
                    Math.round(leg.path("summary").path("time").asDouble()),
                    Polyline.decode(leg.path("shape").asText(), 6)));
        }
        if (legs.size() != points.size() - 1) {
            log.warn("Valhalla trả {} chặng cho {} điểm", legs.size(), points.size());
            throw new RoutingUnavailableException();
        }
        return new RoadRoute(trip.path("summary").path("length").asDouble(),
                Math.round(trip.path("summary").path("time").asDouble()), legs);
    }

    private JsonNode readOrNull(String body) {
        try {
            return json.readTree(body);
        } catch (IOException e) {
            return null;
        }
    }
}
