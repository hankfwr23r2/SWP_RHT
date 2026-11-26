package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.AppUser;
import com.example.racehorse_transport.entity.DepartureVehicle;
import com.example.racehorse_transport.dto.departure.DepartureVehicleResponseDto;
import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.VehicleClass;
import com.example.racehorse_transport.entity.enums.DepartureAllotmentType;
import com.example.racehorse_transport.entity.enums.DepartureAvailability;
import com.example.racehorse_transport.policy.DepartureFleetPolicy.Stock;
import java.util.List;

public interface DepartureFleetService {
    VehicleClass vehicleClass(Long id);
    List<VehicleClass> vehicleClasses();
    long sellableCapacity(Departure departure);
    java.util.Optional<Stock> privateStock(Departure departure, int stalls, boolean lockHub);

    void reservePrivate(Departure departure, com.example.racehorse_transport.entity.Booking booking, int stalls);

    VehicleClass privateVehicleClass(Long bookingId);

    void releasePrivate(Departure departure, Long bookingId);

    List<Stock> freeStock(Departure departure, boolean lockHub);
    DepartureAvailability availability(Departure departure, long spareCapacity);
    void attach(Departure departure, VehicleClass vehicleClass, int count, DepartureAllotmentType type, AppUser actor);
    List<DepartureVehicleResponseDto> vehicles(Long departureId);
    List<DepartureVehicle> allotments(Long departureId);
    void remove(DepartureVehicle allotment, Long actorId, String reason);
    void notifyReinforcement(Departure departure, int count);
}
