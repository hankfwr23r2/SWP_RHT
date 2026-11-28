package com.example.racehorse_transport.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

class CorsConfigTest {

    @Test
    void allowsOnlyTheConfiguredOrigins() {
        CorsConfiguration configuration = new CorsConfig()
                .corsConfigurationSource(" https://app.example.com , http://localhost:* ")
                .getCorsConfiguration(new MockHttpServletRequest("GET", "/bookings"));

        assertThat(configuration).isNotNull();
        assertThat(configuration.checkOrigin("https://app.example.com")).isEqualTo("https://app.example.com");
        assertThat(configuration.checkOrigin("http://localhost:5173")).isEqualTo("http://localhost:5173");
        // Trang của người khác trên cùng dịch vụ host không được gọi API kèm cookie
        assertThat(configuration.checkOrigin("https://attacker.vercel.app")).isNull();
        assertThat(configuration.getAllowCredentials()).isTrue();
    }

    @Test
    void refusesEmptyOrWildcardOrigins() {
        assertThatThrownBy(() -> CorsConfig.parseOrigins(" , ")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> CorsConfig.parseOrigins("*")).isInstanceOf(IllegalStateException.class);
    }
}
