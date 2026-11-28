package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.CrewAssignment;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.VehicleTrip;
import java.util.List;

public interface TripProgressNoticeService {
    void planChanged(VehicleTrip trip, List<CrewAssignment> crew, List<HorseLeg> legs);
    void notifyEarlyStart(VehicleTrip trip, String reason);
    void notifyLinehaulDeparted(VehicleTrip trip);
    void notifyEarlyOvernightDeparture(TripStop stop, String earlyReason);
    void departed(TripStop stop);
    void checkHeldTrips();
}
