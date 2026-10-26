package com.example.racehorse_transport.routing;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

// Máy chủ HTTP giả trong test: trả ĐÚNG mẫu phản hồi thật của Valhalla/Vietmap đã lưu ở src/test/resources/routing.
// Đây là chỗ giả lập duy nhất của các test lộ trình, vì CI không gọi dịch vụ ngoài.
public final class RoutingStubServer implements AutoCloseable {

    public record Recorded(String method, String path, String query, String body) {
        public Map<String, List<String>> params() {
            Map<String, List<String>> params = new LinkedHashMap<>();
            if (query == null || query.isBlank()) return params;
            for (String pair : query.split("&")) {
                String[] kv = pair.split("=", 2);
                params.computeIfAbsent(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), k -> new ArrayList<>())
                        .add(kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
            }
            return params;
        }
    }

    private record Reply(int status, String body) {}

    private final HttpServer server;
    private final Map<String, Reply> replies = new ConcurrentHashMap<>();
    private final List<Recorded> requests = new CopyOnWriteArrayList<>();

    public RoutingStubServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String path = exchange.getRequestURI().getPath();
            requests.add(new Recorded(exchange.getRequestMethod(), path, exchange.getRequestURI().getRawQuery(), body));
            Reply reply = replies.getOrDefault(path, new Reply(404, "{}"));
            byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void reply(String path, int status, String resource) {
        replies.put(path, new Reply(status, fixture(resource)));
    }

    public void replyRaw(String path, int status, String body) {
        replies.put(path, new Reply(status, body));
    }

    public List<Recorded> requests(String path) {
        return requests.stream().filter(r -> r.path().equals(path)).toList();
    }

    public void clearRequests() {
        requests.clear();
    }

    public static String fixture(String name) {
        try (InputStream in = RoutingStubServer.class.getResourceAsStream("/routing/" + name)) {
            if (in == null) throw new IllegalArgumentException("Không có mẫu " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
