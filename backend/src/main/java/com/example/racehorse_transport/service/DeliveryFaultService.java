package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.incident.HandoverCauseRequestDto;
import com.example.racehorse_transport.dto.incident.IncidentResponseDto;

public interface DeliveryFaultService {
    IncidentResponseDto amendCause(Long id, HandoverCauseRequestDto request, String username);
}
