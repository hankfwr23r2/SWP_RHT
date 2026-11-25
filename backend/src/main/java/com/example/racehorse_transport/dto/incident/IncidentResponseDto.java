package com.example.racehorse_transport.dto.incident;

import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.entity.IncidentPlan;
import com.example.racehorse_transport.entity.enums.IncidentCauseType;
import com.example.racehorse_transport.entity.enums.IncidentPlanStatus;
import com.example.racehorse_transport.entity.enums.IncidentResolutionType;
import com.example.racehorse_transport.entity.enums.IncidentStatus;
import com.example.racehorse_transport.entity.enums.IncidentType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record IncidentResponseDto(
        Long id, IncidentType incidentType, IncidentCauseType causeType, IncidentStatus status, String title, String description,
        Long vehicleTripId, String vehicleTripCode, Long horseLegId, String horseName, String bookingCode,
        BigDecimal latitude, BigDecimal longitude, String reportedByName, Instant reportedAt,
        String fitConfirmedByName, Instant fitConfirmedAt, Instant resolvedAt, boolean requiresFit, List<PlanDto> plans, IncidentCauseType confirmedCauseType,
        String causeConfirmedByName, Instant causeConfirmedAt) {

    public record PlanDto(Long id, Integer version, IncidentResolutionType resolutionType, String instructions, BigDecimal estimatedCost,
                          Long handlerPartyId, String handlerPartyName, IncidentPlanStatus status, String proposedByName, Instant proposedAt,
                          String decidedByName, Instant decidedAt, String rejectionReason, Instant customerAgreedAt) {
        public static PlanDto from(IncidentPlan plan) {
            var handler = plan.getHandlerParty();
            return new PlanDto(plan.getId(), plan.getVersion(), plan.getResolutionType(), plan.getInstructions(), plan.getEstimatedCost(),
                    handler == null ? null : handler.getId(), handler == null ? null : handler.getDisplayName(), plan.getStatus(),
                    plan.getProposedBy().getFullName(), plan.getProposedAt(),
                    plan.getDecidedBy() == null ? null : plan.getDecidedBy().getFullName(), plan.getDecidedAt(), plan.getRejectionReason(), plan.getCustomerAgreedAt());
        }
    }

    public static IncidentResponseDto from(Incident incident, boolean requiresFit, List<PlanDto> plans) {
        var trip = incident.getVehicleTrip();
        var leg = incident.getHorseLeg();
        var booking = incident.getBooking();
        return new IncidentResponseDto(incident.getId(), incident.getIncidentType(), incident.getCauseType(), incident.getStatus(),
                incident.getTitle(), incident.getDescription(),
                trip == null ? null : trip.getId(), trip == null ? null : trip.getCode(),
                leg == null ? null : leg.getId(), leg == null ? null : leg.getBookingHorse().getHorse().getName(),
                booking == null ? null : booking.getCode(), incident.getLatitude(), incident.getLongitude(),
                incident.getReportedBy().getFullName(), incident.getReportedAt(),
                incident.getFitConfirmedBy() == null ? null : incident.getFitConfirmedBy().getFullName(), incident.getFitConfirmedAt(),
                incident.getResolvedAt(), requiresFit, plans, incident.getConfirmedCauseType(),
                incident.getCauseConfirmedBy() == null ? null : incident.getCauseConfirmedBy().getFullName(),
                incident.getCauseConfirmedAt());
    }
}
