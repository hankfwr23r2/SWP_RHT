package com.example.racehorse_transport.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.util.OffsetClock;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class LocalDemoPropertiesTest {

    private static MockEnvironment profiles(String... names) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(names);
        return environment;
    }

    @Test
    void startsOnlyWithADatabaseOnThisMachine() {
        assertThat(new LocalDemoProperties("demo", "jdbc:postgresql://localhost:5432/db", profiles("local-demo"))).isNotNull();
        assertThat(new LocalDemoProperties("demo", "jdbc:postgresql://postgres:5432/db", profiles("local-demo"))).isNotNull();
        assertThatThrownBy(() -> new LocalDemoProperties("demo", "jdbc:postgresql://pg.example.com:5432/db", profiles("local-demo")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new LocalDemoProperties("demo", "jdbc:postgresql://localhost,pg.example.com/db", profiles("local-demo")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesProductionProfile() {
        assertThatThrownBy(() -> new LocalDemoProperties("demo", "jdbc:postgresql://localhost:5432/db", profiles("local-demo", "production")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void offsetClockFollowsTheBaseClockPlusOffset() {
        Instant base = Instant.parse("2026-11-25T00:00:00Z");
        OffsetClock clock = new OffsetClock(Clock.fixed(base, ZoneOffset.UTC), Duration.ZERO);
        assertThat(clock.instant()).isEqualTo(base);
        clock.setOffset(Duration.ofHours(30));
        assertThat(clock.instant()).isEqualTo(base.plus(Duration.ofHours(30)));
    }
}
