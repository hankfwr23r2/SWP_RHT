package com.example.racehorse_transport.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.dto.operation.UnclaimedHorseResolutionRequestDto;
import com.example.racehorse_transport.entity.HorseStay;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.enums.HandoverType;
import com.example.racehorse_transport.entity.enums.UnclaimedHorseOutcome;
import org.junit.jupiter.api.Test;

class UnclaimedHorseResolutionRequestDtoTest {

    @Test
    void mapsOnlyTheResolutionAndServerLoadedRelationships() {
        var stay = new HorseStay();
        var manager = new Staff();
        var dto = new UnclaimedHorseResolutionRequestDto(UnclaimedHorseOutcome.RELEASED_BY_LEGAL_DOCUMENT, "Recipient");

        var handover = dto.toEntity(stay, manager);

        assertThat(handover.getHorseStay()).isSameAs(stay);
        assertThat(handover.getHandledBy()).isSameAs(manager);
        assertThat(handover.getHandoverType()).isEqualTo(HandoverType.RETURN);
        assertThat(handover.getUnclaimedOutcome()).isEqualTo(UnclaimedHorseOutcome.RELEASED_BY_LEGAL_DOCUMENT);
        assertThat(handover.getId()).isNull();
        assertThat(handover.getStatus()).isNull();
        assertThat(handover.getHandedAt()).isNull();
    }
}
