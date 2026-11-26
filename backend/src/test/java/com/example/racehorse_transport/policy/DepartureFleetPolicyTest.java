package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.entity.enums.DepartureAvailability;
import com.example.racehorse_transport.entity.enums.DepartureStatus;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.policy.DepartureFleetPolicy.Stock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DepartureFleetPolicyTest {
    private final Stock light = new Stock(1L, 2, 2);
    private final Stock medium = new Stock(2L, 6, 1);
    private final Stock heavy = new Stock(3L, 9, 1);
    private final List<Stock> stock = List.of(heavy, light, medium);

    @Test
    void selectsSmallestSufficientClassAndUsesLargestWhenNoneSuffices() {
        assertThat(DepartureFleetPolicy.selectSpareVehicles(6, stock)).containsOnlyKeys(medium);
        assertThat(DepartureFleetPolicy.selectSpareVehicles(11, stock)).containsEntry(heavy, 1).containsEntry(light, 1).hasSize(2);
        assertThat(DepartureFleetPolicy.selectSpareVehicles(20, stock)).isEmpty();
    }

    @Test
    void busyWindowIncludesReturnLegAndRest() {
        Instant start = Instant.parse("2026-12-01T00:00:00Z");
        assertThat(DepartureFleetPolicy.busyUntil(start, 90, 12)).isEqualTo("2026-12-01T15:00:00Z");
    }

    @Test
    void labelsNearlyFullOnlyWithoutSpareVehicles() {
        assertThat(DepartureFleetPolicy.availability(10, 8, 0, 80)).isEqualTo(DepartureAvailability.NEARLY_FULL);
        assertThat(DepartureFleetPolicy.availability(10, 10, 0, 80)).isEqualTo(DepartureAvailability.FULL);
        assertThat(DepartureFleetPolicy.availability(10, 10, 2, 80)).isEqualTo(DepartureAvailability.AVAILABLE);
        assertThat(DepartureFleetPolicy.availability(10, 7, 0, 80)).isEqualTo(DepartureAvailability.AVAILABLE);
    }

    @Test
    void removalPreservesReservationsAndAtLeastOneAllotment() {
        assertThat(DepartureFleetPolicy.capacityAfterRemoval(DepartureStatus.CLOSED, 2, 18, 9, 9)).isEqualTo(9);
        assertThatThrownBy(() -> DepartureFleetPolicy.capacityAfterRemoval(DepartureStatus.OPEN, 2, 18, 9, 10))
                .isInstanceOf(BookingRuleException.class);
        assertThatThrownBy(() -> DepartureFleetPolicy.capacityAfterRemoval(DepartureStatus.OPEN, 1, 9, 9, 0))
                .isInstanceOf(BookingRuleException.class);
        assertThatThrownBy(() -> DepartureFleetPolicy.capacityAfterRemoval(DepartureStatus.IN_PROGRESS, 2, 18, 9, 0))
                .isInstanceOf(BookingRuleException.class);
    }
}
