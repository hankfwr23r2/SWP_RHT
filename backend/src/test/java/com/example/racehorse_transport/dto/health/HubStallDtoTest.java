package com.example.racehorse_transport.dto.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.entity.HorseStay;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.enums.HubStallKind;
import com.example.racehorse_transport.entity.enums.HorseStayType;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class HubStallDtoTest {
    @Test
    void createsOnlyRequestFieldsAndDisplaysServiceSuppliedOccupancy() {
        Location hub = new Location();
        var stall = new HubStallCreateRequestDto(" I-3 ", HubStallKind.ISOLATION).toEntity(hub);

        assertThat(stall.getId()).isNull();
        assertThat(stall.getLocation()).isSameAs(hub);
        assertThat(stall.isActive()).isFalse();
        assertThat(HubStallResponseDto.from(stall, true))
                .isEqualTo(new HubStallResponseDto(null, "I-3", HubStallKind.ISOLATION, false, true));
    }

    @Test
    void changesOnlyTheStallOnExistingStay() {
        HorseStay stay = new HorseStay();
        stay.setId(20L);
        stay.setStayType(HorseStayType.ASSEMBLY);
        stay.setActualStartAt(Instant.EPOCH);
        var stall = new HubStallCreateRequestDto("S-1", HubStallKind.STANDARD).toEntity(new Location());

        new HubStallChangeRequestDto(6L).applyTo(stay, stall);

        assertThat(stay.getHubStall()).isSameAs(stall);
        assertThat(stay.getId()).isEqualTo(20L);
        assertThat(stay.getStayType()).isEqualTo(HorseStayType.ASSEMBLY);
        assertThat(stay.getActualStartAt()).isEqualTo(Instant.EPOCH);
        assertThat(stall.getId()).isNull();
    }
}
