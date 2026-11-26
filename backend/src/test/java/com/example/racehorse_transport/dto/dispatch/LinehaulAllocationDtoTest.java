package com.example.racehorse_transport.dto.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.dto.dispatch.LinehaulSuggestionResponseDto.LinehaulHorseDto;
import com.example.racehorse_transport.dto.dispatch.LinehaulSuggestionResponseDto.PlateDto;
import com.example.racehorse_transport.dto.dispatch.LinehaulSuggestionResponseDto.TruckDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.DepartureVehicle;
import com.example.racehorse_transport.entity.Horse;
import com.example.racehorse_transport.entity.Vehicle;
import com.example.racehorse_transport.entity.VehicleClass;
import com.example.racehorse_transport.entity.enums.DepartureAllotmentType;
import java.util.List;
import org.junit.jupiter.api.Test;

class LinehaulAllocationDtoTest {
    @Test
    void exposesVehicleClassPlateBookingHorseAndServerComputedAllocation() {
        var booking = new Booking();
        booking.setCode("BK-7");
        var horse = new Horse();
        horse.setName("Sao Mai");
        var line = new BookingHorse();
        line.setId(21L);
        line.setBooking(booking);
        line.setHorse(horse);
        var vehicleClass = new VehicleClass();
        vehicleClass.setName("Xe tải trung");
        vehicleClass.setMaxStallCount(6);
        var allotment = new DepartureVehicle();
        allotment.setId(12L);
        allotment.setVehicleClass(vehicleClass);
        allotment.setAllotmentType(DepartureAllotmentType.SPARE);
        var vehicle = new Vehicle();
        vehicle.setId(7L);
        vehicle.setLicensePlate("51C-012");

        var loads = List.of(LinehaulHorseDto.from(line, 2, "Điểm giao"));
        var plates = List.of(PlateDto.from(vehicle));
        assertThat(loads).containsExactly(new LinehaulHorseDto(21, "Sao Mai", "BK-7", 2, "Điểm giao"));
        assertThat(plates).containsExactly(new PlateDto(7, "51C-012"));
        assertThat(TruckDto.from(allotment, 1, 2, loads, plates)).isEqualTo(
                new TruckDto(12, 1, DepartureAllotmentType.SPARE, "Xe tải trung", 6, 2, loads, plates));
    }
}
