package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.entity.HorseStay;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.enums.LocationType;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class HealthCheckRecentLogPolicyTest {

    private static final Instant NOW = Instant.parse("2026-12-25T10:00:00Z");

    private static HorseStay stay(long id, LocationType type, Instant endedAt) {
        Location location = new Location();
        location.setLocationType(type);
        HorseStay stay = new HorseStay();
        stay.setId(id);
        stay.setLocation(location);
        stay.setActualStartAt(NOW.minusSeconds(86_400));
        stay.setActualEndAt(endedAt);
        return stay;
    }

    @Test
    void logAtTheHubWithinTheWindowCountsAsExitCheck() {
        HorseStay hub = stay(5, LocationType.HUB, null);
        assertThat(HealthCheckTaskPolicy.recentHubCheck(NOW.minusSeconds(30 * 60), hub, null, NOW, 12)).isTrue();
        assertThat(HealthCheckTaskPolicy.recentHubCheck(NOW.minusSeconds(12 * 3600), hub, null, NOW, 12)).isTrue();
        assertThat(HealthCheckTaskPolicy.recentHubCheck(NOW.minusSeconds(12 * 3600 + 1), hub, null, NOW, 12)).isFalse();
    }

    @Test
    void logMustBeAtTheOpenHubStayBeingLeft() {
        assertThat(HealthCheckTaskPolicy.recentHubCheck(NOW.minusSeconds(60), stay(5, LocationType.HUB, NOW.minusSeconds(10)), null, NOW, 12)).isFalse();
        assertThat(HealthCheckTaskPolicy.recentHubCheck(NOW.minusSeconds(60), stay(5, LocationType.STABLE, null), null, NOW, 12)).isFalse();
        assertThat(HealthCheckTaskPolicy.recentHubCheck(NOW.minusSeconds(60), null, null, NOW, 12)).isFalse();
        // Giao cho người nhận tại hub: phải đúng ca lưu trú đang giao
        assertThat(HealthCheckTaskPolicy.recentHubCheck(NOW.minusSeconds(60), stay(5, LocationType.HUB, null), 6L, NOW, 12)).isFalse();
        assertThat(HealthCheckTaskPolicy.recentHubCheck(NOW.minusSeconds(60), stay(6, LocationType.HUB, null), 6L, NOW, 12)).isTrue();
    }
}
