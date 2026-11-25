package com.example.racehorse_transport.dto.operation;

import com.example.racehorse_transport.entity.Handover;
import com.example.racehorse_transport.entity.HorseStay;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.enums.HandoverType;
import com.example.racehorse_transport.entity.enums.UnclaimedHorseOutcome;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UnclaimedHorseResolutionRequestDto(
        @NotNull UnclaimedHorseOutcome outcome, @NotBlank @Size(max = 120) String recipientName) {

    public Handover toEntity(HorseStay stay, Staff manager) {
        var handover = new Handover();
        handover.setHorseStay(stay);
        handover.setHandledBy(manager);
        handover.setHandoverType(HandoverType.RETURN);
        handover.setUnclaimedOutcome(outcome);
        return handover;
    }
}
