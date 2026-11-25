package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.operation.HandoverRefusalRequestDto;
import com.example.racehorse_transport.entity.BookingHorse;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

public interface PickupMissEvidenceService {
    void record(BookingHorse horse, HandoverRefusalRequestDto request, MultipartFile photo, Instant arrivedAt,
                Instant at, Long actorId);
    void audit(Long actorId, String action, Long horseId, Map<String, String> data);
}
