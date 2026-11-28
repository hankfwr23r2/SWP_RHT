package com.example.racehorse_transport.dto.departure;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.dto.booking.BookingDelayResponseDto;
import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.enums.DepartureStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DepartureDelayDtoTest {
    @Test
    void requestUpdatesOnlyTheRevisedTimeAndReason() {
        var departure = new Departure();
        var original = Instant.parse("2027-01-15T03:00:00Z");
        departure.setId(15L);
        departure.setStatus(DepartureStatus.CLOSED);
        departure.setPlannedDepartureAt(original);
        var revised = original.plusSeconds(3600);
        new DepartureDelayRequestDto(revised, "  Thay xe  ").applyTo(departure);
        assertThat(departure.getRevisedDepartureAt()).isEqualTo(revised);
        assertThat(departure.getDelayReason()).isEqualTo("Thay xe");
        assertThat(departure.getId()).isEqualTo(15L);
        assertThat(departure.getStatus()).isEqualTo(DepartureStatus.CLOSED);
        assertThat(departure.getPlannedDepartureAt()).isEqualTo(original);
        assertThat(departure.getDelayedAt()).isNull();
        assertThat(departure.getRunConfirmedBy()).isNull();
    }

    @Test
    void responseMapsOriginalAndRevisedTimesAndTheOptionsSuppliedByService() {
        var departure = new Departure();
        var original = Instant.parse("2027-01-15T03:00:00Z");
        departure.setId(5L);
        departure.setPlannedDepartureAt(original);
        departure.setRevisedDepartureAt(original.plusSeconds(7200));
        departure.setDelayReason("Thay xe");
        var response = BookingDelayResponseDto.from(departure, true, false, false);
        assertThat(response.departureId()).isEqualTo(5L);
        assertThat(response.plannedDepartureAt()).isEqualTo(original);
        assertThat(response.revisedDepartureAt()).isEqualTo(original.plusSeconds(7200));
        assertThat(response.canRescheduleFree()).isTrue();
        assertThat(response.canRefundFull()).isFalse();
    }
}
