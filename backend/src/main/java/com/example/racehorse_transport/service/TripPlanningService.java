package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.dispatch.VehicleTripCreateRequestDto;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.Vehicle;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.policy.DispatchPolicy.Interval;
import java.util.List;
import java.util.Set;

public interface TripPlanningService {
    Long createTrip(VehicleTripCreateRequestDto request, String username, boolean manuallyTimed);

    Set<Long> manualTripIds(List<Long> ids);

    void requireLocalStops(VehicleTrip trip, List<TripStop> planned);

    void requireVehicleSchedule(Vehicle vehicle, VehicleTrip trip, Interval window);
}
