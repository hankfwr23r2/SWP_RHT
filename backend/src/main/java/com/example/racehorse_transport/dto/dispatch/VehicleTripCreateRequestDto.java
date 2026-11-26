package com.example.racehorse_transport.dto.dispatch;

import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Vehicle;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.VehicleTripType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

// LINEHAUL: chỉ cần departureId, điểm dừng chép từ tuyến của chuyến (stops bỏ qua).
// Loại khác: stops theo thứ tự, điểm đầu và điểm cuối là điểm mạng lưới, địa chỉ của đơn ở giữa.
public record VehicleTripCreateRequestDto(
        @NotNull VehicleTripType tripType,
        @Positive Long departureId,
        @NotNull @Positive Long vehicleId,
        @Valid @Size(max = 50) List<TripStopRequestDto> stops) {

    public List<TripStopRequestDto> stopsOrEmpty() {
        return stops == null ? List.of() : stops;
    }

    // Mã và trạng thái do service đặt
    public VehicleTrip toEntity(Vehicle vehicle, Departure departure, Location start, Location end,
                                Instant plannedStartAt, Instant plannedEndAt) {
        VehicleTrip trip = new VehicleTrip();
        trip.setTripType(tripType);
        trip.setDeparture(departure);
        trip.setVehicle(vehicle);
        trip.setStartLocation(start);
        trip.setEndLocation(end);
        trip.setPlannedStartAt(plannedStartAt);
        trip.setPlannedEndAt(plannedEndAt);
        return trip;
    }
}
