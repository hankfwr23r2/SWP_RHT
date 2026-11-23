package com.example.racehorse_transport.dto.report;

import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripType;
import java.math.BigDecimal;
import java.time.Instant;

// Một dòng báo cáo chuyến: lượt xe kèm số liệu đúng giờ, ngựa, sự cố, chi phí, đánh giá của khách
public record TripReportRowResponseDto(
        Long id, String code, VehicleTripType tripType, VehicleTripStatus status, String licensePlate,
        String startLocationName, String endLocationName, Instant plannedStartAt, Instant actualStartAt, Instant actualEndAt,
        int stopCount, int arrivedStopCount, int lateStopCount, int horseCount, int incidentCount, BigDecimal costTotal,
        Double averageRating) {
}
