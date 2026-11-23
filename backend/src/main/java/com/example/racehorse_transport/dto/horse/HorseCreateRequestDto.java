package com.example.racehorse_transport.dto.horse;

import com.example.racehorse_transport.entity.Horse;
import com.example.racehorse_transport.entity.HorseType;
import com.example.racehorse_transport.entity.enums.HorseGender;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record HorseCreateRequestDto(
        @NotBlank @Size(max = 30) String microchip,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 50) String passportNumber,
        @NotNull HorseGender sex,
        @Min(1980) @Max(2100) Short birthYear,
        @Size(max = 50) String color,
        @Size(max = 100) String breed,
        @Size(max = 100) String sire,
        @Size(max = 100) String dam,
        @NotNull @Positive Long horseTypeId,
        @Size(max = 2000) String identificationMarks,
        @DecimalMin("0.1") @Digits(integer = 5, fraction = 1) BigDecimal weightKg,
        @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal declaredValue,
        @Pattern(regexp = "[A-Z]{3}") String declaredCurrencyCode) {

    // Chủ ngựa, mã chip đã chuẩn hóa, trạng thái và giá trị khai báo do service đặt
    public Horse toEntity(HorseType horseType) {
        Horse horse = new Horse();
        horse.setName(name.strip());
        horse.setPassportNumber(text(passportNumber));
        horse.setSex(sex);
        horse.setBirthYear(birthYear);
        horse.setColor(text(color));
        horse.setBreed(text(breed));
        horse.setSire(text(sire));
        horse.setDam(text(dam));
        horse.setHorseType(horseType);
        horse.setIdentificationMarks(text(identificationMarks));
        horse.setWeightKg(weightKg);
        return horse;
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
