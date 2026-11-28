package com.example.racehorse_transport.dto.departure;

import com.example.racehorse_transport.entity.VehicleClass;
import com.example.racehorse_transport.entity.DepartureVehicle;
import com.example.racehorse_transport.entity.enums.DepartureAllotmentType;
import com.example.racehorse_transport.entity.enums.DepartureAvailability;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Route;
import com.example.racehorse_transport.entity.enums.DepartureStatus;
import java.time.LocalDate;
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
    void responseCarriesRouteEndsAndGivenAvailability() {
        Departure departure = new Departure();
        departure.setId(10L);
        departure.setRoute(route());
        departure.setDepartureDate(LocalDate.of(2027, 1, 5));
        departure.setStallCapacity(9);
        departure.setBookedStallCount(7);
        departure.setStatus(DepartureStatus.OPEN);

        DepartureResponseDto dto = DepartureResponseDto.from(departure, 2);

        assertThat(dto.getDepartureScheduleId()).isNull();
        assertThat(dto.getRouteCode()).isEqualTo("HCM-DN");
        assertThat(dto.getOriginCode()).isEqualTo("HUB-HCM");
        assertThat(dto.getDestinationCode()).isEqualTo("HUB-DN");
        assertThat(dto.getAvailableStalls()).isEqualTo(2);

        var customer = DepartureResponseDto.forCustomer(departure, DepartureAvailability.NEARLY_FULL);
        assertThat(customer.getAvailability()).isEqualTo(DepartureAvailability.NEARLY_FULL);
        assertThat(customer.getStallCapacity()).isNull();
        assertThat(customer.getBookedStallCount()).isNull();
        assertThat(customer.getAvailableStalls()).isNull();
    }

    @Test
    void allotmentResponseCarriesItsClassAndReason() {
        var vehicleClass = new VehicleClass();
        vehicleClass.setId(4L);
        vehicleClass.setCode("HEAVY");
        var allotment = new DepartureVehicle();
        allotment.setId(5L);
        allotment.setVehicleClass(vehicleClass);
        allotment.setVehicleCount(2);
        allotment.setAllotmentType(DepartureAllotmentType.SPARE);
        allotment.setReason("Reinforcement");

        assertThat(DepartureVehicleResponseDto.from(allotment)).isEqualTo(
                new DepartureVehicleResponseDto(5L, 4L, "HEAVY", 2, DepartureAllotmentType.SPARE, "Reinforcement"));
    }
}
