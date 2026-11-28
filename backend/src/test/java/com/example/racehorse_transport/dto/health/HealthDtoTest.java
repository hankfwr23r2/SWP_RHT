package com.example.racehorse_transport.dto.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.Horse;
import com.example.racehorse_transport.entity.HorseHealthLog;
import com.example.racehorse_transport.entity.HorseStay;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.enums.HealthStatus;
import com.example.racehorse_transport.entity.enums.HorseStayType;
import com.example.racehorse_transport.entity.enums.IntakeLevel;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class HealthDtoTest {

    @Test
    void createsLogWithoutRecorderOrTime() {
        BookingHorse line = new BookingHorse();
        TripStop stop = new TripStop();
        var request = new HealthLogRequestDto(1L, 2L, null, HealthStatus.NORMAL, new BigDecimal("37.8"), (short) 40, (short) 12, null,
                IntakeLevel.NORMAL, IntakeLevel.REDUCED, "  ", " Ăn chậm ", null, null);

        HorseHealthLog log = request.toEntity(line, stop, null);

        assertThat(log.getId()).isNull();
        assertThat(log.getRecordedBy()).isNull();
        assertThat(log.getRecordedAt()).isNull();
        assertThat(log.getBookingHorse()).isSameAs(line);
        assertThat(log.getTripStop()).isSameAs(stop);
        assertThat(log.getHorseStay()).isNull();
        assertThat(log.getWaterIntake()).isEqualTo(IntakeLevel.NORMAL);
        assertThat(log.getFeedIntake()).isEqualTo(IntakeLevel.REDUCED);
        assertThat(log.getSymptoms()).isNull();
        assertThat(log.getNote()).isEqualTo("Ăn chậm");
    }

    @Test
    void describesLogAtAStayAndTheStay() {
        Booking booking = new Booking();
        booking.setCode("BK-ABCDEFGH");
        Horse horse = new Horse();
        horse.setName("Sao Mai");
        BookingHorse line = new BookingHorse();
        line.setId(4L);
        line.setBooking(booking);
        line.setHorse(horse);
        Location hub = new Location();
        hub.setId(1L);
        hub.setName("Hub HCM");
        HorseStay stay = new HorseStay();
        stay.setId(6L);
        stay.setBookingHorse(line);
        stay.setLocation(hub);
        stay.setStayType(HorseStayType.ASSEMBLY);
        stay.setActualStartAt(Instant.parse("2031-03-05T05:00:00Z"));
        Staff escort = new Staff();
        escort.setFullName("Hộ tống Một");
        HorseHealthLog log = new HorseHealthLog();
        log.setId(9L);
        log.setBookingHorse(line);
        log.setHorseStay(stay);
        log.setRecordedBy(escort);
        log.setHealthStatus(HealthStatus.WATCH);
        log.setWaterIntake(IntakeLevel.REFUSED);

        var response = HealthLogResponseDto.from(log);
        assertThat(response.placeName()).isEqualTo("Hub HCM");
        assertThat(response.horseStayId()).isEqualTo(6L);
        assertThat(response.tripStopId()).isNull();
        assertThat(response.recordedByName()).isEqualTo("Hộ tống Một");

        line.setDietNote("Hay only");
        line.setFeedSuppliedByOwner(true);
        var stayDto = HorseStayResponseDto.from(stay);
        assertThat(stayDto.bookingCode()).isEqualTo("BK-ABCDEFGH");
        assertThat(stayDto.locationName()).isEqualTo("Hub HCM");
        assertThat(stayDto.actualEndAt()).isNull();
        assertThat(stayDto.dietNote()).isEqualTo("Hay only");
        assertThat(stayDto.feedingsPerDay()).isNull();
        assertThat(stayDto.feedSuppliedByOwner()).isTrue();
        assertThat(stayDto.hubStallCode()).isNull();
        var stall = new com.example.racehorse_transport.entity.HubStall();
        stall.setCode("S-01");
        stay.setHubStall(stall);
        var active = HorseStayResponseDto.from(stay, false, true, true);
        assertThat(active.hubStallCode()).isEqualTo("S-01");
        assertThat(active.canChangeHubStall()).isTrue();
        assertThat(active.suggestIsolation()).isTrue();
    }
}
