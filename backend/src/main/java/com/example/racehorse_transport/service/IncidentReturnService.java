package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.incident.IncidentResponseDto;
import com.example.racehorse_transport.entity.enums.IncidentResolutionType;

// Hai phương án dừng chặng đang chạy và đưa ngựa vào hub ở phía sau.
public interface IncidentReturnService {
    IncidentResponseDto returnHorses(Long incidentId, IncidentResolutionType type, Long planId, String username);
}
