package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.incident.IncidentReportRequestDto;

public interface IncidentReportingService {
    java.util.List<Long> coordinatorRecipients(com.example.racehorse_transport.entity.Incident incident);
    Long report(IncidentReportRequestDto request, String username);
}
