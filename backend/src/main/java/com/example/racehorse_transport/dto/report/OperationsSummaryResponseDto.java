package com.example.racehorse_transport.dto.report;

import java.math.BigDecimal;
import java.util.List;

// Số liệu vận hành trong một khoảng (bảng điều khiển của quản lý)
public record OperationsSummaryResponseDto(
        int tripCount, int completedTripCount, int horsesDelivered,
        int arrivedStopCount, int lateStopCount, Double onTimeRate,
        int incidentCount, int openIncidentCount, List<IncidentTypeCountDto> incidentsByType,
        BigDecimal costTotal, List<PayerAmountDto> costsByPayer,
        Double averageRating, int ratingCount) {

    public record IncidentTypeCountDto(String key, long count) {
    }

    public record PayerAmountDto(String key, BigDecimal amount) {
    }
}
