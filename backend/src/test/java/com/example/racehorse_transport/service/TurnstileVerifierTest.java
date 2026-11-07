package com.example.racehorse_transport.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TurnstileVerifierTest {
    @Test
    void rejectsRequestsWhenSecretIsNotConfigured() {
        TurnstileVerifier verifier = new TurnstileVerifier("", "http://localhost");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> verifier.verify("token", "customer_login"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
    }

    @Test
    void rejectsRequestsWithoutAToken() {
        TurnstileVerifier verifier = new TurnstileVerifier("secret", "http://localhost");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> verifier.verify(" ", "customer_login"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void acceptsSuccessfulVerificationForTheExpectedAction() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = verificationServer("customer_login", requestBody);
        server.start();
        try {
            TurnstileVerifier verifier = new TurnstileVerifier(
                    "test-secret",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/siteverify");

            verifier.verify("test-response", "customer_login");

            assertEquals("secret=test-secret&response=test-response", requestBody.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsSuccessfulVerificationForAnotherAction() throws IOException {
        HttpServer server = verificationServer("staff_login", new AtomicReference<>());
        server.start();
        try {
            TurnstileVerifier verifier = new TurnstileVerifier(
                    "test-secret",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/siteverify");

            ResponseStatusException exception = assertThrows(
                    ResponseStatusException.class,
                    () -> verifier.verify("test-response", "customer_login"));

            assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        } finally {
            server.stop(0);
        }
    }

    private HttpServer verificationServer(String action, AtomicReference<String> requestBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/siteverify", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = ("{\"success\":true,\"action\":\"" + action + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        return server;
    }
}
