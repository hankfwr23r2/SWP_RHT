package com.example.racehorse_transport.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.exception.GeocodeUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GeocodeClientTest {
    private HttpServer server;
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<String> query = new AtomicReference<>();
    private final AtomicReference<String> agent = new AtomicReference<>();

    private String serve(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search", exchange -> {
            calls.incrementAndGet();
            query.set(exchange.getRequestURI().getRawQuery());
            agent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/search";
    }

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    private static Clock at(long second) {
        return Clock.fixed(Instant.ofEpochSecond(1_800_000_000L + second), ZoneOffset.UTC);
    }

    @Test
    void returnsAtMostFiveValidPlacesAndCachesTheAnswer() throws IOException {
        List<String> places = new ArrayList<>();
        for (int i = 0; i < 7; i++) places.add("{\"display_name\":\"Nơi " + i + "\",\"lat\":\"10.7" + i + "\",\"lon\":\"106.6\"}");
        places.add(1, "{\"display_name\":\"\",\"lat\":\"10\",\"lon\":\"106\"}");
        String url = serve(200, "[" + String.join(",", places) + "]");
        var client = new GeocodeClient(url, new ObjectMapper(), at(0));

        var results = client.search("Phường Bình Trị Đông", "vn");

        assertThat(results).hasSize(5);
        assertThat(results.get(0).label()).isEqualTo("Nơi 0");
        assertThat(results.get(1).label()).isEqualTo("Nơi 1");
        assertThat(results.get(0).latitude()).isEqualTo(10.70);
        assertThat(query.get()).contains("countrycodes=vn").contains("limit=5");
        assertThat(agent.get()).isEqualTo(ValhallaClient.USER_AGENT);
        assertThat(client.search("Phường Bình Trị Đông", "vn")).isEqualTo(results);
        assertThat(calls).hasValue(1);
    }

    @Test
    void reportsUnavailableWhenTheServiceFailsOrIsNotConfigured() throws IOException {
        var failing = new GeocodeClient(serve(503, "busy"), new ObjectMapper(), at(0));
        assertThatThrownBy(() -> failing.search("Huế", "vn")).isInstanceOf(GeocodeUnavailableException.class);

        var disabled = new GeocodeClient("", new ObjectMapper(), at(0));
        assertThatThrownBy(() -> disabled.search("Huế", "vn")).isInstanceOf(GeocodeUnavailableException.class);
    }
}
