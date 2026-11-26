package com.example.racehorse_transport.dto.operation;

import com.example.racehorse_transport.entity.HandoverHorse;
import com.example.racehorse_transport.entity.enums.MicrochipEntryMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// Nhập chip một con trong lần giao nhận, kèm tình trạng lúc giao
public record HandoverScanRequestDto(
        @NotNull @Positive Long horseLegId,
        @NotBlank @Size(max = 30) String microchip,
        @Size(max = 1000) String conditionNote,
        @Schema(defaultValue = "SCAN") MicrochipEntryMethod entryMethod,
        @Size(max = 500, message = "Lý do gõ tay không được vượt quá 500 ký tự.") String manualReason) {

    public void applyTo(HandoverHorse horse, MicrochipEntryMethod method, String reason) {
        horse.setScannedMicrochip(microchip.strip());
        horse.setMicrochipEntryMethod(method);
        horse.setManualEntryReason(reason);
        horse.setConditionNote(conditionNote == null || conditionNote.isBlank() ? null : conditionNote.strip());
    }
}
