package com.example.racehorse_transport.dto.health;

import com.example.racehorse_transport.entity.HorseHealthLog;
import com.example.racehorse_transport.entity.enums.HealthStatus;
import com.example.racehorse_transport.entity.enums.IntakeLevel;
import java.math.BigDecimal;
import java.time.Instant;

// placeName: tên điểm dừng hoặc nơi lưu trú
public record HealthLogResponseDto(
        Long id, Long bookingHorseId, String horseName, String recordedByName, Instant recordedAt,
        Long tripStopId, Long horseStayId, String placeName,
        HealthStatus healthStatus, BigDecimal temperatureCelsius, Short heartRateBpm, Short respirationRatePerMinute, BigDecimal weightKg,
        IntakeLevel waterIntake, IntakeLevel feedIntake, String symptoms, String note) {

    public static HealthLogResponseDto from(HorseHealthLog log) {
        var stop = log.getTripStop();
        var stay = log.getHorseStay();
        String place = stop != null ? (stop.getLocation() != null ? stop.getLocation().getName() : null)
                : stay != null ? stay.getLocation().getName() : null;
        return new HealthLogResponseDto(log.getId(), log.getBookingHorse().getId(), log.getBookingHorse().getHorse().getName(),
                log.getRecordedBy().getFullName(), log.getRecordedAt(),
                stop == null ? null : stop.getId(), stay == null ? null : stay.getId(), place,
                log.getHealthStatus(), log.getTemperatureCelsius(), log.getHeartRateBpm(), log.getRespirationRatePerMinute(), log.getWeightKg(),
                log.getWaterIntake(), log.getFeedIntake(), log.getSymptoms(), log.getNote());
    }
}
