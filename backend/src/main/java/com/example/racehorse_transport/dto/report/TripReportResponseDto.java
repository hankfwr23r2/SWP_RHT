package com.example.racehorse_transport.dto.report;

import com.example.racehorse_transport.entity.enums.CostCategory;
import com.example.racehorse_transport.entity.enums.CostPayerType;
import com.example.racehorse_transport.entity.enums.HealthStatus;
import com.example.racehorse_transport.entity.enums.IncidentCauseType;
import com.example.racehorse_transport.entity.enums.IncidentStatus;
import com.example.racehorse_transport.entity.enums.IncidentType;
import com.example.racehorse_transport.entity.enums.IntakeLevel;
import com.example.racehorse_transport.entity.enums.TripStopType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// Báo cáo một lượt xe (docs/NGHIEP_VU_THEO_59_BANG.md: dựng từ trip_stop, horse_health_log, incident, cost_item, booking_rating)
public record TripReportResponseDto(
        TripReportRowResponseDto summary, List<ReportStopDto> stops, List<ReportHealthDto> healthLogs,
        List<ReportIncidentDto> incidents, List<ReportCostDto> costs, List<ReportRatingDto> ratings) {

    public record ReportStopDto(Integer sequenceNumber, TripStopType stopType, String name,
                                Instant plannedArrivalAt, Instant actualArrivalAt, Instant actualDepartureAt, boolean late) {
    }

    public record ReportHealthDto(String horseName, Instant recordedAt, HealthStatus healthStatus, BigDecimal temperatureCelsius,
                                  IntakeLevel waterIntake, IntakeLevel feedIntake, String symptoms) {
    }

    public record ReportIncidentDto(Long id, String title, IncidentType incidentType, IncidentCauseType causeType, IncidentStatus status,
                                    Instant reportedAt, Instant resolvedAt) {
    }

    public record ReportCostDto(CostCategory category, String description, BigDecimal amount, CostPayerType payerType, boolean approved) {
    }

    public record ReportRatingDto(String bookingCode, Short score, String comment) {
    }
}
