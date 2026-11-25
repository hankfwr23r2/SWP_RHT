package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.CrewAssignment;
import com.example.racehorse_transport.entity.Handover;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.TripStop;
import java.util.List;
import java.util.Set;

public interface HandoverCustodyService {

    CrewAssignment requireCrew(Long tripId, String username);

    void lockCurrentStop(TripStop stop);

    CrewAssignment lockForCompletion(Long handoverId, String username);

    void prepareStableHandover(Handover handover, Long partyId, Long planId);

    List<HorseLeg> stableCandidates(Handover handover);

    Set<Long> legsAtStable(TripStop stop);

    void finishStablePlan(Handover handover, List<HorseLeg> matched);
}
