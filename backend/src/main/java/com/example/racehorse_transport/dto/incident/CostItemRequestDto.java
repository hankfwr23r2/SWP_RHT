package com.example.racehorse_transport.dto.incident;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.CostItem;
import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.CostCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

// Chi phí phát sinh gắn với sự cố, lượt xe hoặc đơn. Bên chịu do service xác định theo nguyên nhân sự cố
public record CostItemRequestDto(
        @NotNull CostCategory category,
        @NotBlank @Size(max = 500) String description,
        @NotNull @Positive BigDecimal amount,
        @Pattern(regexp = "^[A-Z]{3}$") String currencyCode,
        @Positive Long incidentId,
        @Positive Long vehicleTripId,
        @Positive Long bookingId,
        @NotNull Instant incurredAt) {

    public String currencyOrDefault() {
        return currencyCode == null ? "VND" : currencyCode;
    }

    // Bên chịu, người ghi, người duyệt do service đặt
    public CostItem toEntity(Incident incident, VehicleTrip trip, Booking booking) {
        CostItem cost = new CostItem();
        cost.setCategory(category);
        cost.setDescription(description.strip());
        cost.setAmount(amount);
        cost.setCurrencyCode(currencyOrDefault());
        cost.setIncident(incident);
        cost.setVehicleTrip(trip);
        cost.setBooking(booking);
        cost.setIncurredAt(incurredAt);
        return cost;
    }
}
