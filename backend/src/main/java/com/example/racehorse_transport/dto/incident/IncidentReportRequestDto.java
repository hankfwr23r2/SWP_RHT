package com.example.racehorse_transport.dto.incident;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.IncidentCauseType;
import com.example.racehorse_transport.entity.enums.IncidentType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

// Báo sự cố cho cả lượt xe (horseLegId null) hoặc cho một con trên lượt
public record IncidentReportRequestDto(
        @NotNull @Positive Long vehicleTripId,
        @Positive Long horseLegId,
        @NotNull IncidentType incidentType,
        @NotNull IncidentCauseType causeType,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 4000) String description,
        @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude) {

    // Người báo và trạng thái do service đặt
    public Incident toEntity(VehicleTrip trip, HorseLeg leg, Booking booking) {
        Incident incident = new Incident();
        incident.setVehicleTrip(trip);
        incident.setHorseLeg(leg);
        incident.setBooking(booking);
        incident.setIncidentType(incidentType);
        incident.setCauseType(causeType);
        incident.setTitle(title.strip());
        incident.setDescription(description.strip());
        incident.setLatitude(latitude);
        incident.setLongitude(longitude);
        return incident;
    }
}
