package com.example.racehorse_transport.dto.tracking;

import com.example.racehorse_transport.dto.route.GeoPointDto;
import com.example.racehorse_transport.entity.enums.HealthStatus;
import com.example.racehorse_transport.entity.enums.HorseLegType;
import com.example.racehorse_transport.entity.enums.HorseStayType;
import com.example.racehorse_transport.policy.TrackingPolicy.CustodyHolder;
import java.time.Instant;

// Một con ngựa công ty hoặc đối tác đang giữ: ở đâu, ai giữ, lần khám gần nhất và hạn khám tới.
// stayType có khi đang lưu trú; legType, tripCode, vehicleTripId có khi đang trên đường. point: vị trí biết được gần nhất
public record CustodyRowResponseDto(
        Long bookingHorseId, String horseName, Long bookingId, String bookingCode,
        CustodyHolder holder, String holderName, Instant holderSince,
        HorseStayType stayType, HorseLegType legType, Long vehicleTripId, String tripCode, String placeName, GeoPointDto point,
        Instant lastCheckAt, HealthStatus healthStatus, Instant nextCheckAt, boolean careOverdue) {
}
