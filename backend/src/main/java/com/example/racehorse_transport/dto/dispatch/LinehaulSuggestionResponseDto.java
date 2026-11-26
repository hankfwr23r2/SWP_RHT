package com.example.racehorse_transport.dto.dispatch;

import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.DepartureVehicle;
import com.example.racehorse_transport.entity.Vehicle;
import com.example.racehorse_transport.entity.enums.DepartureAllotmentType;
import java.util.List;

public record LinehaulSuggestionResponseDto(int missingSeats, List<TruckDto> trucks) {
    public record TruckDto(long allotmentId, int number, DepartureAllotmentType allotmentType,
                           String vehicleClassName, int capacity, int usedSeats, List<LinehaulHorseDto> horses,
                           List<PlateDto> vehicles) {
        public static TruckDto from(DepartureVehicle allotment, int number, int usedSeats,
                                    List<LinehaulHorseDto> horses, List<PlateDto> vehicles) {
            return new TruckDto(allotment.getId(), number, allotment.getAllotmentType(), allotment.getVehicleClass().getName(),
                    allotment.getVehicleClass().getMaxStallCount(), usedSeats, horses, vehicles);
        }
    }

    public record LinehaulHorseDto(long bookingHorseId, String horseName, String bookingCode, int cluster, String deliveryAddress) {
        public static LinehaulHorseDto from(BookingHorse horse, int cluster, String deliveryAddress) {
            return new LinehaulHorseDto(horse.getId(), horse.getHorse().getName(), horse.getBooking().getCode(), cluster, deliveryAddress);
        }
    }

    public record PlateDto(long id, String licensePlate) {
        public static PlateDto from(Vehicle vehicle) {
            return new PlateDto(vehicle.getId(), vehicle.getLicensePlate());
        }
    }
}
