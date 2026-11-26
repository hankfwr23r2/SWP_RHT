package com.example.racehorse_transport.dto.departure;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.dto.booking.BookingMergeSuggestionResponseDto;
import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.enums.DepartureStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DepartureDecisionOptionsDtoTest {

    @Test
    void confirmRequestUpdatesOnlyTheCapacityWhenSupplied() {
        Departure departure = new Departure();
        departure.setId(10L);
        departure.setStatus(DepartureStatus.CLOSED);
        departure.setStallCapacity(9);
        new DepartureConfirmRunRequestDto(null).applyTo(departure);
        assertThat(departure.getStallCapacity()).isEqualTo(9);

        new DepartureConfirmRunRequestDto(2).applyTo(departure);
        assertThat(departure.getStallCapacity()).isEqualTo(2);
        assertThat(departure.getId()).isEqualTo(10L);
        assertThat(departure.getStatus()).isEqualTo(DepartureStatus.CLOSED);
        assertThat(departure.getRunConfirmedAt()).isNull();
        assertThat(departure.getRunConfirmedBy()).isNull();
    }

    @Test
    void mergeRequestLinksTheLoadedTargetAndLeavesStatusToTheService() {
        Departure source = new Departure();
        source.setId(9L);
        source.setStatus(DepartureStatus.CLOSED);
        Departure target = new Departure();
        target.setId(10L);

        new DepartureMergeRequestDto(10L, "Ít khách").applyTo(source, target);

        assertThat(source.getMergedIntoDeparture()).isSameAs(target);
        assertThat(source.getId()).isEqualTo(9L);
        assertThat(source.getStatus()).isEqualTo(DepartureStatus.CLOSED);
    }

    @Test
    void mapsTheTargetAndTheSuggestedPickupDateForTheCustomer() {
        Departure target = new Departure();
        target.setId(10L);
        target.setDepartureDate(LocalDate.parse("2026-12-15"));
        LocalDate pickup = target.getDepartureDate().minusDays(1);

        assertThat(BookingMergeSuggestionResponseDto.from(target, pickup))
                .isEqualTo(new BookingMergeSuggestionResponseDto(10L, target.getDepartureDate(), pickup));
    }
}
