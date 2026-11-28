package com.example.racehorse_transport.dto.horse;

import com.example.racehorse_transport.entity.Horse;
import com.example.racehorse_transport.entity.HorseType;
import com.example.racehorse_transport.entity.enums.HorseGender;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// Không có mã chip: không đổi sau khi lưu
public record HorseUpdateRequestDto(
        @NotBlank @Size(max = 100) String name,
        @NotNull HorseGender sex,
        @NotNull(message = "Nhập năm sinh hợp lệ.")
        @Min(value = 1990, message = "Nhập năm sinh hợp lệ.")
        @Max(value = 2100, message = "Nhập năm sinh hợp lệ.") Short birthYear,
        @NotBlank(message = "Nhập màu lông.") @Size(max = 50, message = "Màu lông tối đa 50 ký tự.") String color,
        @NotNull @Positive Long horseTypeId,
        @Size(max = 2000) String identificationMarks) {

    public void applyTo(Horse horse, HorseType horseType) {
        horse.setName(name.strip());
        horse.setSex(sex);
        horse.setBirthYear(birthYear);
        horse.setColor(color.strip());
        horse.setHorseType(horseType);
        horse.setIdentificationMarks(text(identificationMarks));
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
