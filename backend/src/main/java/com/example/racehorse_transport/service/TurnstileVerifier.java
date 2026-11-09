package com.example.racehorse_transport.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class TurnstileVerifier {
    private static final Logger logger = LoggerFactory.getLogger(TurnstileVerifier.class);
    private final RestClient restClient;
    private final String secretKey;
    private final String verifyUrl;

    public TurnstileVerifier(
            @Value("${app.turnstile.secret-key:}") String secretKey,
            @Value("${app.turnstile.verify-url:https://challenges.cloudflare.com/turnstile/v0/siteverify}") String verifyUrl) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.secretKey = secretKey == null ? "" : secretKey.trim();
        this.verifyUrl = verifyUrl;
    }

    public void verify(String token, String expectedAction) {
        // Nếu secretKey chưa được cấu hình → bỏ qua verify (dev/no-captcha mode)
        if (secretKey.isEmpty()) {
            logger.warn("Turnstile secret key is not configured – skipping CAPTCHA verification.");
            return;
        }
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please complete the CAPTCHA challenge.");
        }

        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secretKey);
        form.add("response", token);
        TurnstileResponse result;
        try {
            result = restClient.post()
                    .uri(verifyUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TurnstileResponse.class);
        } catch (RestClientResponseException exception) {
            logger.error("Turnstile verification endpoint returned HTTP {}", exception.getStatusCode().value());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "CAPTCHA verification is temporarily unavailable.");
        } catch (RestClientException exception) {
            logger.error("Could not reach the Turnstile verification endpoint", exception);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "CAPTCHA verification is temporarily unavailable.");
        }

        if (result == null || !result.success() || !expectedAction.equals(result.action())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CAPTCHA verification failed. Please try again.");
        }
    }

    private record TurnstileResponse(boolean success, String action) {}
}
