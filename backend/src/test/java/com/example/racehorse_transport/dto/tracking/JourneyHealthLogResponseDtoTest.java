package com.example.racehorse_transport.dto.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.entity.BookingAddress;
import com.example.racehorse_transport.entity.HorseHealthLog;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.HealthStatus;
import com.example.racehorse_transport.entity.enums.IntakeLevel;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class JourneyHealthLogResponseDtoTest {

    @Test
    void mapsMeasurementsAndAddressAtCustomerStop() {
        var address = new BookingAddress();
        address.setAddressLine("Stable gate");
        var stop = new TripStop();
        stop.setBookingAddress(address);
        var log = new HorseHealthLog();
        log.setId(7L);
        log.setRecordedAt(Instant.parse("2026-11-25T09:00:00Z"));
        log.setTripStop(stop);
        log.setHealthStatus(HealthStatus.WATCH);
        log.setWaterIntake(IntakeLevel.REDUCED);
        log.setFeedIntake(IntakeLevel.NORMAL);
        log.setTemperatureCelsius(new BigDecimal("38.2"));

        assertThat(JourneyHealthLogResponseDto.from(log)).isEqualTo(new JourneyHealthLogResponseDto(
                7L, log.getRecordedAt(), "Stable gate", null, HealthStatus.WATCH, IntakeLevel.REDUCED,
                IntakeLevel.NORMAL, new BigDecimal("38.2")));
    }

    @Test
    void mapsEnRouteLocationAndKeepsUnrecordedMeasurementsNull() {
        var trip = new VehicleTrip();
        trip.setCode("TR-1");
        var log = new HorseHealthLog();
        log.setVehicleTrip(trip);

        var response = JourneyHealthLogResponseDto.from(log);
        assertThat(response.vehicleTripCode()).isEqualTo("TR-1");
        assertThat(response.placeName()).isNull();
        assertThat(response.waterIntake()).isNull();
        assertThat(response.feedIntake()).isNull();
        assertThat(response.temperatureCelsius()).isNull();
    }
}
