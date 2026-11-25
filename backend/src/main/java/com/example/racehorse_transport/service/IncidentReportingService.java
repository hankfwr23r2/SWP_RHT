package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.incident.IncidentReportRequestDto;

public interface IncidentReportingService {
    Long report(IncidentReportRequestDto request, String username);
}
