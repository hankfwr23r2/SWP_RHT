package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.operation.CrewTripResponseDto;
import com.example.racehorse_transport.dto.operation.HandoverCreateRequestDto;
import com.example.racehorse_transport.dto.operation.HandoverScanRequestDto;

// Giao nhận ngựa tại điểm dừng (B2). Chủ sở hữu handover, handover_horse.
public interface HandoverService {

    // Lượt xe nhìn từ tổ xe; chỉ người trong tổ lái (chưa bị gỡ) mới xem được
    CrewTripResponseDto crewTrip(Long vehicleTripId, String username);

    CrewTripResponseDto create(Long tripStopId, HandoverCreateRequestDto request, String username);

    CrewTripResponseDto scan(Long handoverId, HandoverScanRequestDto request, String username);

    // Con quét đúng chip được nhận lên xe (chặng IN_PROGRESS) hoặc giao xong (chặng COMPLETED); con cuối của đơn giao xong thì đơn DELIVERED (#16)
    CrewTripResponseDto complete(Long handoverId, String username);
}
