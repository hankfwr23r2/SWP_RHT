package com.example.racehorse_transport.dto.tracking;

import com.example.racehorse_transport.entity.HorseHealthLog;
import com.example.racehorse_transport.entity.enums.HealthStatus;
import com.example.racehorse_transport.entity.enums.IntakeLevel;
import java.math.BigDecimal;
import java.time.Instant;

public record JourneyHealthLogResponseDto(Long id, Instant recordedAt, String placeName, String vehicleTripCode,
                                          HealthStatus healthStatus, IntakeLevel waterIntake, IntakeLevel feedIntake,
                                          BigDecimal temperatureCelsius) {

    public static JourneyHealthLogResponseDto from(HorseHealthLog log) {
        var stop = log.getTripStop();
        var stay = log.getHorseStay();
        var trip = log.getVehicleTrip();
        String place = stop != null ? HorseJourneyResponseDto.nameOf(stop.getLocation(), stop.getBookingAddress())
                : stay != null ? stay.getLocation().getName() : null;
        return new JourneyHealthLogResponseDto(log.getId(), log.getRecordedAt(), place, trip == null ? null : trip.getCode(),
                log.getHealthStatus(), log.getWaterIntake(), log.getFeedIntake(), log.getTemperatureCelsius());
    }
}
