package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.VehicleTrip;

public interface TripProgressNoticeService {
    void notifyEarlyStart(VehicleTrip trip, String reason);
    void notifyLinehaulDeparted(VehicleTrip trip);
    void notifyEarlyOvernightDeparture(TripStop stop, String earlyReason);
    void departed(TripStop stop);
    void checkHeldTrips();
}
