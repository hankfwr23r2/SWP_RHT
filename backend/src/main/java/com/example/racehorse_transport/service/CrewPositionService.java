package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.dispatch.CrewCandidateResponseDto;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.CrewRole;
import java.util.List;

public interface CrewPositionService {
    void requireAtStart(Staff member, VehicleTrip trip);

    List<CrewCandidateResponseDto> candidates(CrewRole role, Long tripId);
}
