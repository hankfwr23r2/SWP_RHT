package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.dispatch.CrewCandidateResponseDto;
import com.example.racehorse_transport.entity.CrewAssignment;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.CrewRole;
import com.example.racehorse_transport.policy.DispatchPolicy.Interval;
import java.util.List;

public interface CrewPositionService {
    void requireSchedule(Staff member, CrewRole role, VehicleTrip trip, boolean midway);

    List<CrewAssignment> revalidateTrip(VehicleTrip trip, Interval previousWindow, List<Interval> stopTimes);

    List<CrewCandidateResponseDto> candidates(CrewRole role, Long tripId);
}
