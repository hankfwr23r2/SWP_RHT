package com.example.racehorse_transport.dto.health;

import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.HorseHealthLog;
import com.example.racehorse_transport.entity.HorseStay;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.enums.HealthStatus;
import com.example.racehorse_transport.entity.enums.IntakeLevel;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

// Một lần đo, ghi ăn uống của một con tại điểm dừng (tripStopId) hoặc trong lần lưu trú (horseStayId)
public record HealthLogRequestDto(
        @NotNull @Positive Long bookingHorseId,
        @Positive Long tripStopId,
        @Positive Long horseStayId,
        @NotNull HealthStatus healthStatus,
        @DecimalMin("30.0") @DecimalMax("45.0") BigDecimal temperatureCelsius,
        @Min(10) @Max(200) Short heartRateBpm,
        @Min(4) @Max(100) Short respirationRatePerMinute,
        @Positive BigDecimal weightKg,
        IntakeLevel waterIntake,
        IntakeLevel feedIntake,
        @Size(max = 2000) String symptoms,
        @Size(max = 2000) String note) {

    // Người ghi và giờ ghi do service đặt
    public HorseHealthLog toEntity(BookingHorse bookingHorse, TripStop stop, HorseStay stay) {
        HorseHealthLog log = new HorseHealthLog();
        log.setBookingHorse(bookingHorse);
        log.setTripStop(stop);
        log.setHorseStay(stay);
        log.setHealthStatus(healthStatus);
        log.setTemperatureCelsius(temperatureCelsius);
        log.setHeartRateBpm(heartRateBpm);
        log.setRespirationRatePerMinute(respirationRatePerMinute);
        log.setWeightKg(weightKg);
        log.setWaterIntake(waterIntake);
        log.setFeedIntake(feedIntake);
        log.setSymptoms(symptoms == null || symptoms.isBlank() ? null : symptoms.strip());
        log.setNote(note == null || note.isBlank() ? null : note.strip());
        return log;
    }
}
