package com.example.racehorse_transport.dto.departure;

import com.example.racehorse_transport.entity.Departure;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record DepartureMergeRequestDto(
        @NotNull(message = "Chọn chuyến đích.") @Positive(message = "Chuyến đích không hợp lệ.") Long targetDepartureId,
        @NotBlank(message = "Nhập lý do dồn chuyến.") @Size(max = 1000, message = "Lý do tối đa 1000 ký tự.") String reason) {

    public void applyTo(Departure source, Departure target) {
        source.setMergedIntoDeparture(target);
    }
}
