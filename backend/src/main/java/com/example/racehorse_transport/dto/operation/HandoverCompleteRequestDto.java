package com.example.racehorse_transport.dto.operation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record HandoverCompleteRequestDto(Map<String, @NotNull @Valid DeliveryRecipientRequestDto> recipients) {
    public DeliveryRecipientRequestDto forBooking(String bookingCode) {
        return recipients == null ? null : recipients.get(bookingCode);
    }
}
