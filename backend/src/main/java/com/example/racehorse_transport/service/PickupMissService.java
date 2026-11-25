package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.operation.PickupMissChoiceRequestDto;
import com.example.racehorse_transport.dto.operation.PickupMissLiabilityRequestDto;
import com.example.racehorse_transport.dto.operation.PickupMissResponseDto;
import com.example.racehorse_transport.dto.operation.HandoverRefusalRequestDto;
import org.springframework.web.multipart.MultipartFile;
import java.time.Instant;
import java.util.List;

public interface PickupMissService {
    void record(Long horseId, String reason, Instant at, Long actorId);
    void recordAtStop(Long horseId, HandoverRefusalRequestDto request, MultipartFile photo,
                      Instant arrivedAt, Instant at, Long actorId);
    void requireSelfDeliveryBeforeCheckIn(Long horseId);

    List<PickupMissResponseDto> list(Long bookingId, String username);
    PickupMissResponseDto decide(Long horseId, PickupMissChoiceRequestDto request, String username);
    PickupMissResponseDto assignLiability(Long horseId, PickupMissLiabilityRequestDto request, String username);
    List<Long> expired();
    void expire(Long horseId);
}
