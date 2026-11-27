package com.example.racehorse_transport.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class JwtPropertiesTest {

    private static final String LOCAL_DB = "jdbc:postgresql://localhost:5432/db";
    private static final String REMOTE_DB = "jdbc:postgresql://db.example.com:5432/db";
    private static final String STRONG = "k".repeat(JwtProperties.MIN_SECRET_BYTES);

    private static MockEnvironment profiles(String... names) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(names);
        return environment;
    }

    @Test
    void usesTheConfiguredSecret() {
        JwtProperties properties = new JwtProperties(STRONG, REMOTE_DB, profiles("prod"));
        assertThat(properties.signingKey()).isEqualTo(STRONG.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void refusesToStartWithoutASecretOnADeployedDatabase() {
        assertThatThrownBy(() -> new JwtProperties("", REMOTE_DB, profiles()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtProperties(" ", LOCAL_DB, profiles("production")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesShortAndLeakedSecrets() {
        assertThatThrownBy(() -> new JwtProperties("ngan", LOCAL_DB, profiles()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtProperties(JwtProperties.LEAKED_SECRET, LOCAL_DB, profiles()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtProperties(JwtProperties.LEAKED_SECRET.toLowerCase(), REMOTE_DB, profiles()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void devMachineWithoutSecretGetsARandomKey() {
        byte[] first = new JwtProperties("", LOCAL_DB, profiles()).signingKey();
        byte[] second = new JwtProperties("", "jdbc:postgresql://postgres:5432/db", profiles()).signingKey();
        assertThat(first).hasSizeGreaterThanOrEqualTo(JwtProperties.MIN_SECRET_BYTES);
        assertThat(first).isNotEqualTo(second);
    }
}
