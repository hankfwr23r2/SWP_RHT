package com.example.racehorse_transport.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
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
    private final boolean testKey;

    public TurnstileVerifier(
            @Value("${app.turnstile.secret-key:}") String secretKey,
            @Value("${app.turnstile.verify-url:https://challenges.cloudflare.com/turnstile/v0/siteverify}") String verifyUrl,
            Environment environment) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.secretKey = secretKey == null ? "" : secretKey.trim();
        this.verifyUrl = verifyUrl;
        this.testKey = java.util.Set.of(
                "1x0000000000000000000000000000000AA",
                "2x0000000000000000000000000000000AA",
                "3x0000000000000000000000000000000AA").contains(this.secretKey);
        if (testKey && (!environment.acceptsProfiles(Profiles.of("local-turnstile-test"))
                || environment.acceptsProfiles(Profiles.of("prod", "production")))) {
            throw new IllegalArgumentException("Turnstile test keys require the local-turnstile-test profile and cannot be used in production.");
        }
    }

    public void verify(String token, String expectedAction) {
        if (secretKey.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "CAPTCHA verification is not configured.");
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

        // Cloudflare's official test responses omit action. Real keys always require the expected action.
        if (result == null || !result.success()
                || !(expectedAction.equals(result.action()) || (testKey && result.action() == null))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CAPTCHA verification failed. Please try again.");
        }
    }

    private record TurnstileResponse(boolean success, String action) {}
}
