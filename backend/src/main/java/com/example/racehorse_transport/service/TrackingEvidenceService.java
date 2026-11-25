package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.tracking.JourneyHealthLogResponseDto;
import com.example.racehorse_transport.entity.Document;
import java.util.List;
import java.util.Map;

public interface TrackingEvidenceService {

    Map<Long, List<Document>> handoverEvidence(Long bookingId, List<Long> handoverIds);

    Map<Long, List<JourneyHealthLogResponseDto>> healthLogsByHorse(Long bookingId);
}
