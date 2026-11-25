package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.tracking.JourneyHealthLogResponseDto;
import com.example.racehorse_transport.entity.Document;
import com.example.racehorse_transport.repository.DocumentRepository;
import com.example.racehorse_transport.repository.HorseHealthLogRepository;
import com.example.racehorse_transport.service.TrackingEvidenceService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrackingEvidenceServiceImpl implements TrackingEvidenceService {

    private final DocumentRepository documents;
    private final HorseHealthLogRepository healthLogs;

    @Override
    public Map<Long, List<Document>> handoverEvidence(Long bookingId, List<Long> handoverIds) {
        if (handoverIds.isEmpty()) return Map.of();
        return documents.findHandoverEvidenceForBooking(bookingId, handoverIds).stream()
                .collect(Collectors.groupingBy(document -> document.getHandover().getId()));
    }

    @Override
    public Map<Long, List<JourneyHealthLogResponseDto>> healthLogsByHorse(Long bookingId) {
        return healthLogs.findByBookingHorse_Booking_IdOrderByRecordedAtDescIdDesc(bookingId).stream()
                .collect(Collectors.groupingBy(log -> log.getBookingHorse().getId(),
                        Collectors.mapping(JourneyHealthLogResponseDto::from, Collectors.toList())));
    }
}
