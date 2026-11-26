package com.example.racehorse_transport.dto.operation;

import com.example.racehorse_transport.entity.enums.PickupMissReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// Không nhận một con lúc nhận ngựa từ khách (chip sai, không đủ sức khỏe), bắt buộc có lý do
public record HandoverRefusalRequestDto(
        @NotNull @Positive Long horseLegId,
        @NotBlank @Size(max = 500) String reason,
        @NotNull PickupMissReason reasonCode,
        @Size(max = 500) String senderContact,
        @Size(max = 500) String customerContact,
        @Size(max = 500) String emergencyContact) {}
