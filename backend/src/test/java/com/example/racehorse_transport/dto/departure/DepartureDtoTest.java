package com.example.racehorse_transport.dto.departure;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Route;
import com.example.racehorse_transport.entity.enums.DepartureStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class DepartureDtoTest {

    private static Route route() {
        Location origin = new Location();
        origin.setCode("HUB-HCM");
        Location destination = new Location();
        destination.setCode("HUB-DN");
        Route route = new Route();
        route.setId(4L);
        route.setCode("HCM-DN");
        route.setName("TP.HCM – Đà Nẵng");
        route.setOrigin(origin);
        route.setDestination(destination);
        return route;
    }

    @Test
    void createRequestLeavesStatusAndBookedStallsToService() {
        DepartureCreateRequestDto request = new DepartureCreateRequestDto();
        request.setRouteId(4L);
        request.setDepartureDate(LocalDate.of(2027, 1, 5));
        request.setDepartureTime(LocalTime.of(7, 0));
        request.setBookingCutoffHours(48);
        request.setCapacityStalls(6);
        Instant planned = Instant.parse("2027-01-05T00:00:00Z");
        Instant cutoff = Instant.parse("2027-01-03T00:00:00Z");

        Departure departure = request.toEntity(route(), planned, cutoff);

        assertThat(departure.getId()).isNull();
        assertThat(departure.getSchedule()).isNull();
        assertThat(departure.getStatus()).isNull();
        assertThat(departure.getPlannedDepartAt()).isEqualTo(planned);
        assertThat(departure.getBookingCutoffAt()).isEqualTo(cutoff);
        assertThat(departure.getCapacityStalls()).isEqualTo(6);
    }

    @Test
    void responseCarriesRouteEndsAndGivenAvailability() {
        Departure departure = new Departure();
        departure.setId(10L);
        departure.setRoute(route());
        departure.setDepartureDate(LocalDate.of(2027, 1, 5));
        departure.setCapacityStalls(9);
        departure.setBookedStalls(7);
        departure.setStatus(DepartureStatus.OPEN);

        DepartureResponseDto dto = DepartureResponseDto.from(departure, 2);

        assertThat(dto.getScheduleId()).isNull();
        assertThat(dto.getRouteCode()).isEqualTo("HCM-DN");
        assertThat(dto.getOriginCode()).isEqualTo("HUB-HCM");
        assertThat(dto.getDestinationCode()).isEqualTo("HUB-DN");
        assertThat(dto.getAvailableStalls()).isEqualTo(2);
    }
}
