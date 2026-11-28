package com.example.racehorse_transport.client;

import com.example.racehorse_transport.dto.geocode.GeocodeResultResponseDto;
import com.example.racehorse_transport.exception.GeocodeUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GeocodeClient {
    private record Cached(Instant expiresAt, List<GeocodeResultResponseDto> results) {}

    private final String searchUrl;
    private final ObjectMapper json;
    private final Clock clock;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private final LinkedHashMap<String, Cached> cache = new LinkedHashMap<>(64, .75f, true);
    private Instant nextRequest = Instant.MIN;

    public GeocodeClient(@Value("${routing.geocode.search-url:}") String searchUrl, ObjectMapper json, Clock clock) {
        this.searchUrl = searchUrl;
        this.json = json;
        this.clock = clock;
    }

    public synchronized List<GeocodeResultResponseDto> search(String query, String country) {
        if (searchUrl.isBlank()) throw new GeocodeUnavailableException();
        String params = "?format=jsonv2&limit=5&countrycodes=" + country
                + "&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        Instant now = clock.instant();
        Cached hit = cache.get(params);
        if (hit != null && hit.expiresAt().isAfter(now)) return hit.results();
        if (now.isBefore(nextRequest)) throw new GeocodeUnavailableException();
        nextRequest = now.plusSeconds(1);
        var request = HttpRequest.newBuilder(URI.create(searchUrl + params))
                .header("User-Agent", ValhallaClient.USER_AGENT)
                .timeout(Duration.ofSeconds(5)).GET().build();
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new GeocodeUnavailableException();
            JsonNode body = json.readTree(response.body());
            if (body == null || !body.isArray()) throw new GeocodeUnavailableException();
            List<GeocodeResultResponseDto> results = new ArrayList<>();
            for (JsonNode entry : body) {
                if (results.size() == 5) break;
                String name = entry.path("display_name").asText("");
                double lat = entry.path("lat").asDouble(Double.NaN);
                double lon = entry.path("lon").asDouble(Double.NaN);
                if (!name.isBlank() && Math.abs(lat) <= 90 && Math.abs(lon) <= 180) {
                    results.add(new GeocodeResultResponseDto(name, lat, lon));
                }
            }
            var immutable = List.copyOf(results);
            cache.put(params, new Cached(now.plus(Duration.ofMinutes(5)), immutable));
            if (cache.size() > 256) cache.pollFirstEntry();
            return immutable;
        } catch (IOException e) {
            throw new GeocodeUnavailableException();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GeocodeUnavailableException();
        }
    }
}
