package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.operation.CrewTripResponseDto;
import com.example.racehorse_transport.dto.operation.HandoverCreateRequestDto;
import com.example.racehorse_transport.dto.operation.HandoverRefusalRequestDto;
import com.example.racehorse_transport.dto.operation.HandoverScanRequestDto;

// Giao nhận ngựa tại điểm dừng (B2). Chủ sở hữu handover, handover_horse.
public interface HandoverService {

    // Lượt xe nhìn từ tổ xe; chỉ người trong tổ lái (chưa bị gỡ) mới xem được
    CrewTripResponseDto crewTrip(Long vehicleTripId, String username);

    // RETURN ở điểm đón: quá hạn trả số dư thì trả ngựa cho người gửi và hủy đơn ngay trong một thao tác
    CrewTripResponseDto create(Long tripStopId, HandoverCreateRequestDto request, String username);

    CrewTripResponseDto scan(Long handoverId, HandoverScanRequestDto request, String username);

    // Con quét đúng chip được nhận lên xe (chặng IN_PROGRESS) hoặc giao xong (chặng COMPLETED); con cuối của đơn giao xong thì đơn DELIVERED (#16)
    CrewTripResponseDto complete(Long handoverId, String username);

    // Không nhận một con ở lần nhận ngựa từ khách (PICKUP): con đó bị loại khỏi đơn, các chặng chưa chạy bị hủy; hết con để nhận thì lần giao nhận FAILED
    CrewTripResponseDto refuse(Long handoverId, HandoverRefusalRequestDto request, String username);
}
