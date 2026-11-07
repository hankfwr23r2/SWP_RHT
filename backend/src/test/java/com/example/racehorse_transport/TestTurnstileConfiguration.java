package com.example.racehorse_transport;

import com.example.racehorse_transport.service.TurnstileVerifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
class TestTurnstileConfiguration {
    @Bean
    @Primary
    TurnstileVerifier testTurnstileVerifier() {
        return new TurnstileVerifier("test-secret", "http://127.0.0.1") {
            @Override
            public void verify(String token, String expectedAction) {
            }
        };
    }
}
