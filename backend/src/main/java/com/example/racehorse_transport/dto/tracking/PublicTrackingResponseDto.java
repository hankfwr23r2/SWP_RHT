package com.example.racehorse_transport.dto.tracking;

import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.HorseLegType;
import com.example.racehorse_transport.entity.enums.HorseStayType;
import com.example.racehorse_transport.policy.TrackingPolicy.CustodyHolder;
import java.time.Instant;
import java.util.List;

// Kết quả tra cứu một mã đơn. found = false khi mã không có hoặc sai số điện thoại (không nói rõ để khỏi dò).
// Không trả địa chỉ, tên đối tác: chỉ tên điểm mạng lưới.
public record PublicTrackingResponseDto(String code, boolean found, BookingStatus status, Instant plannedDeliveryAt, List<PublicHorseDto> horses) {

    // Nơi ngựa đang ở: đang lưu trú (stayType) hoặc đang trên chặng (legType); locationName chỉ có khi là điểm mạng lưới
    public record PublicHorseDto(String horseName, CustodyHolder holder, HorseStayType stayType, HorseLegType legType, String locationName, Instant updatedAt) {
    }

    public static PublicTrackingResponseDto notFound(String code) {
        return new PublicTrackingResponseDto(code, false, null, null, List.of());
    }
}
