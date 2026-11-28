package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class CareCheckPolicyTest {

    private static final Instant SINCE = Instant.parse("2027-01-01T00:00:00Z");

    @Test
    void countsFromTheLatestOfReceivingAndLastLog() {
        assertThat(CareCheckPolicy.nextCheckAt(SINCE, null, true, 4)).isEqualTo("2027-01-01T04:00:00Z");
        assertThat(CareCheckPolicy.nextCheckAt(SINCE, SINCE.minusSeconds(3600), true, 4)).isEqualTo("2027-01-01T04:00:00Z");
        assertThat(CareCheckPolicy.nextCheckAt(SINCE, SINCE.plusSeconds(7200), true, 4)).isEqualTo("2027-01-01T06:00:00Z");
    }

    @Test
    void horsesInStayAreCheckedTwiceADay() {
        assertThat(CareCheckPolicy.nextCheckAt(SINCE, null, false, 4)).isEqualTo("2027-01-01T12:00:00Z");
    }

    @Test
    void overdueOnlyAfterTheMoment() {
        assertThat(CareCheckPolicy.overdue(SINCE, SINCE)).isFalse();
        assertThat(CareCheckPolicy.overdue(SINCE, SINCE.plusSeconds(1))).isTrue();
    }
}
