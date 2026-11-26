package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.HorseStay;
import com.example.racehorse_transport.policy.StablingPolicy;
import java.time.Instant;
import java.util.List;

public interface StablingTimelineService {
    List<StablingPolicy.Run> runs(HorseStay stay, Instant through);
    boolean readySettled(HorseStay stay);
    void chargePlannedStay(HorseStay stay, Instant through);
    void chargePlannedStaysOnDeparture(Long tripStopId, Instant departedAt);
}
