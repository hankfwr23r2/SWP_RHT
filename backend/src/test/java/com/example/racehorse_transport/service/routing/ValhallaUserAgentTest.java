package com.example.racehorse_transport.service.routing;

import com.example.racehorse_transport.routing.RoutingStubServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

// Máy chủ Valhalla công khai yêu cầu User-Agent nêu tên ứng dụng; mọi request phải gửi kèm.
class ValhallaUserAgentTest {

    @Test
    void everyRequestIdentifiesTheApplication() throws Exception {
        AtomicReference<String> userAgent = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] body = RoutingStubServer.fixture("valhalla-truck-hcm-mocbai-phnompenh.json").getBytes(StandardCharsets.UTF_8);
        server.createContext("/route", exchange -> {
            userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            new ValhallaClient("http://127.0.0.1:" + server.getAddress().getPort(), new ObjectMapper())
                    .truckRoute(List.of(new GeoPoint(10.7769, 106.7009), new GeoPoint(11.0796, 106.1572),
                            new GeoPoint(11.5564, 104.9282)), 8, 80, LocalDateTime.parse("2026-11-02T05:00"));
        } finally {
            server.stop(0);
        }

        assertThat(userAgent.get()).isEqualTo("racehorse-transport/1.0 (+https://github.com/hankfwr23r2/SWP_RHT)");
    }
}
