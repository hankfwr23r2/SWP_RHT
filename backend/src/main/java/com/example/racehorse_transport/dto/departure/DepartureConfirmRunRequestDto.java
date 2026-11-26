package com.example.racehorse_transport.dto.departure;

import com.example.racehorse_transport.entity.Departure;
import jakarta.validation.constraints.Positive;

public record DepartureConfirmRunRequestDto(@Positive(message = "Số chỗ chạy phải lớn hơn 0.") Integer stallCapacity) {

    public void applyTo(Departure departure) {
        if (stallCapacity != null) {
            departure.setStallCapacity(stallCapacity);
        }
    }
}
