package com.example.racehorse_transport.dto.operation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.Handover;
import com.example.racehorse_transport.entity.HandoverHorse;
import com.example.racehorse_transport.entity.Horse;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.enums.HandoverStatus;
import com.example.racehorse_transport.entity.enums.HandoverType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OperationDtoTest {

    @Test
    void createsHandoverWithoutStatusOrHandedTime() {
        TripStop stop = new TripStop();
        Staff staff = new Staff();

        Handover handover = new HandoverCreateRequestDto(HandoverType.PICKUP, "  Ngựa hơi mệt ", null).toEntity(stop, staff);

        assertThat(handover.getId()).isNull();
        assertThat(handover.getStatus()).isNull();
        assertThat(handover.getHandedAt()).isNull();
        assertThat(handover.getWaitStartedAt()).isNull();
        assertThat(handover.getHandoverType()).isEqualTo(HandoverType.PICKUP);
        assertThat(handover.getTripStop()).isSameAs(stop);
        assertThat(handover.getHandledBy()).isSameAs(staff);
        assertThat(handover.getNote()).isEqualTo("Ngựa hơi mệt");
        assertThat(new HandoverCreateRequestDto(HandoverType.HUB_IN, " ", null).toEntity(stop, staff).getNote()).isNull();
    }

    @Test
    void describesHandoverWithScannedAndPendingHorses() {
        TripStop stop = new TripStop();
        stop.setId(5L);
        Staff staff = new Staff();
        staff.setFullName("Hộ tống Một");
        Handover handover = new Handover();
        handover.setId(9L);
        handover.setHandoverType(HandoverType.DELIVERY);
        handover.setStatus(HandoverStatus.PENDING);
        handover.setTripStop(stop);
        handover.setHandledBy(staff);
        HorseLeg scannedLeg = leg(1L, "Sao Mai");
        HorseLeg waitingLeg = leg(2L, "Gió Bắc");
        HandoverHorse scan = new HandoverHorse();
        scan.setScannedMicrochip("985000000100002");
        scan.setMicrochipMatched(true);
        scan.setConditionNote("Khỏe");

        var response = HandoverResponseDto.from(handover, List.of(scannedLeg, waitingLeg), Map.of(1L, scan), false, java.util.Set.of(1L));
        assertThat(response.horses()).extracting(HandoverResponseDto.HandoverHorseDto::photoAttached).containsExactly(true, false);

        assertThat(response.tripStopId()).isEqualTo(5L);
        assertThat(response.handledByName()).isEqualTo("Hộ tống Một");
        assertThat(response.horses()).hasSize(2);
        assertThat(response.horses().get(0).microchipMatched()).isTrue();
        assertThat(response.horses().get(0).conditionNote()).isEqualTo("Khỏe");
        assertThat(response.horses().get(1).scannedMicrochip()).isNull();
        assertThat(response.horses().get(1).microchipMatched()).isNull();
        assertThat(response.horses().get(1).bookingCode()).isEqualTo("BK-ABCDEFGH");
    }

    private static HorseLeg leg(Long id, String name) {
        Booking booking = new Booking();
        booking.setCode("BK-ABCDEFGH");
        Horse horse = new Horse();
        horse.setName(name);
        BookingHorse line = new BookingHorse();
        line.setBooking(booking);
        line.setHorse(horse);
        HorseLeg leg = new HorseLeg();
        leg.setId(id);
        leg.setBookingHorse(line);
        return leg;
    }
}
