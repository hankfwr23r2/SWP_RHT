package com.example.racehorse_transport.dto.horse;

import com.example.racehorse_transport.entity.Horse;
import com.example.racehorse_transport.entity.enums.HorseGender;
import com.example.racehorse_transport.entity.enums.HorseStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record HorseResponseDto(Long id, String name, String microchip, String passportNumber, HorseGender sex, Short birthYear,
                               String color, String breed, String sire, String dam, Long horseTypeId, String horseTypeName,
                               String identificationMarks, BigDecimal weightKg, BigDecimal declaredValue,
                               String declaredCurrencyCode, HorseStatus status, Instant createdAt, Instant updatedAt) {

    public static HorseResponseDto from(Horse horse) {
        return new HorseResponseDto(horse.getId(), horse.getName(), horse.getMicrochip(), horse.getPassportNumber(),
                horse.getSex(), horse.getBirthYear(), horse.getColor(), horse.getBreed(), horse.getSire(), horse.getDam(),
                horse.getHorseType().getId(), horse.getHorseType().getName(), horse.getIdentificationMarks(),
                horse.getWeightKg(), horse.getDeclaredValue(), horse.getDeclaredCurrencyCode(), horse.getStatus(),
                horse.getCreatedAt(), horse.getUpdatedAt());
    }
}
