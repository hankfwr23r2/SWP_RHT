package com.example.racehorse_transport.service;

import java.time.Duration;
import java.util.Set;

public interface DeparturePlanShiftService {
    Set<Long> lockAffectedTrips(Long departureId);
    void shift(Long departureId, Set<Long> lockedTrips, Duration offset);
}
