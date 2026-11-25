package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.incident.IncidentSeverityRequestDto;
import com.example.racehorse_transport.dto.incident.TripUnlockRequestDto;
import com.example.racehorse_transport.entity.enums.IncidentSeverity;
import com.example.racehorse_transport.entity.enums.IncidentType;
import java.util.Map;

public interface IncidentSeverityService {

    Map<IncidentType, IncidentSeverity> defaults();

    void changeSeverity(Long incidentId, IncidentSeverityRequestDto request, String username);

    void unlockTrip(Long incidentId, TripUnlockRequestDto request, String username);
}
