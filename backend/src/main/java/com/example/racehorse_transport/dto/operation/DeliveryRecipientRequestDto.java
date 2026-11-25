package com.example.racehorse_transport.dto.operation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DeliveryRecipientRequestDto(
        @NotBlank @Size(max = 120) String recipientName,
        @Pattern(regexp = "[0-9]{6}") String collectionCode) {
    @Override
    public String toString() {
        return "DeliveryRecipientRequestDto";
    }
}
