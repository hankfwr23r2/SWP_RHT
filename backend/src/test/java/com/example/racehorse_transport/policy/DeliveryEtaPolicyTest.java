package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DeliveryEtaPolicyTest {
    private final Instant a = Instant.parse("2026-11-25T07:00:00Z");
    private final Instant b = a.plusSeconds(12600);
    private final Instant c = b.plusSeconds(3600);

    @Test
    void cascadesStopTimeAndTravelFromTheActualDeparture() {
        var plannedDeparture = a.plusSeconds(1800);
        var actualDeparture = a.plusSeconds(5400);
        assertThat(DeliveryEtaPolicy.arrival(actualDeparture, plannedDeparture, b)).isEqualTo(b.plusSeconds(3600));
        assertThat(DeliveryEtaPolicy.arrival(actualDeparture, plannedDeparture, c)).isEqualTo(c.plusSeconds(3600));
        assertThat(DeliveryEtaPolicy.arrival(a, plannedDeparture, b)).isEqualTo(b.minusSeconds(1800));
        assertThat(DeliveryEtaPolicy.arrival(plannedDeparture, plannedDeparture, b)).isEqualTo(b);
    }

    @Test
    void announcedToleranceAndChangeThresholdAreInclusive() {
        var told = b.plusSeconds(3600);
        assertThat(DeliveryEtaPolicy.reference(null, b)).isEqualTo(b);
        assertThat(DeliveryEtaPolicy.reference(told, b)).isEqualTo(told);
        assertThat(DeliveryEtaPolicy.companyFault(told, told.plusSeconds(1800), 30)).isFalse();
        assertThat(DeliveryEtaPolicy.companyFault(told, told.plusSeconds(1801), 30)).isTrue();
        assertThat(DeliveryEtaPolicy.companyFault(told, null, 30)).isFalse();
        assertThat(DeliveryEtaPolicy.changed(told, told.minusSeconds(1800), 30)).isFalse();
        assertThat(DeliveryEtaPolicy.changed(told, told.minusSeconds(1801), 30)).isTrue();
        assertThat(DeliveryEtaPolicy.window(told, 30).from()).isEqualTo(told.minusSeconds(1800));
        assertThat(DeliveryEtaPolicy.window(told, 30).to()).isEqualTo(told.plusSeconds(1800));
    }

    @Test
    void considersBothActualArrivalAndPlannedDwellWhenHeld() {
        var arrived = a.plusSeconds(900);
        var plannedDeparture = a.plusSeconds(1800);
        assertThat(DeliveryEtaPolicy.held(arrived, a, plannedDeparture, arrived.plusSeconds(3600), 30)).isFalse();
        assertThat(DeliveryEtaPolicy.held(arrived, a, plannedDeparture, arrived.plusSeconds(3601), 30)).isTrue();
    }

    @Test
    void appliesTheClosingHourToTheDeliveryDateAndEarlierArrivals() {
        var cutoff = Instant.parse("2026-11-25T18:00:00Z");
        assertThat(DeliveryEtaPolicy.afterDeliveryEnd(b, cutoff, 18, ZoneOffset.UTC)).isFalse();
        assertThat(DeliveryEtaPolicy.afterDeliveryEnd(b, cutoff.plusSeconds(1), 18, ZoneOffset.UTC)).isTrue();
        assertThat(DeliveryEtaPolicy.afterDeliveryEnd(b, b.plusSeconds(86400), 18, ZoneOffset.UTC)).isTrue();
        assertThat(DeliveryEtaPolicy.afterDeliveryEnd(b, cutoff.minusSeconds(86399), 18, ZoneOffset.UTC)).isTrue();
    }

    @Test
    void rendersBothDatesWhenTheNoticeWindowCrossesMidnight() {
        var midnightInVietnam = Instant.parse("2026-11-25T17:00:00Z");
        assertThat(CustomerNoticePolicy.deliveryWindowText(DeliveryEtaPolicy.window(midnightInVietnam, 30)))
                .isEqualTo("23:30 ngày 25/11 – 00:30 ngày 26/11");
    }
}
