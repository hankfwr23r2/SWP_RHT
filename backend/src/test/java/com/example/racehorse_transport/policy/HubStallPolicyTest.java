package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.entity.HorseStay;
import com.example.racehorse_transport.entity.HubStall;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Party;
import com.example.racehorse_transport.entity.enums.HubStallKind;
import com.example.racehorse_transport.entity.enums.LocationType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HubStallPolicyTest {
    @Test
    void assignsFirstAvailableStandardByCode() {
        HubStall occupied = stall(1L, "A", HubStallKind.STANDARD, true);
        HubStall disabled = stall(2L, "B", HubStallKind.STANDARD, false);
        HubStall isolation = stall(3L, "C", HubStallKind.ISOLATION, true);
        HubStall free = stall(4L, "D", HubStallKind.STANDARD, true);
        HubStall later = stall(5L, "E", HubStallKind.STANDARD, true);

        assertThat(HubStallPolicy.firstAvailableStandard(List.of(later, isolation, disabled, occupied, free), Set.of(1L)))
                .contains(free);
        assertThat(HubStallPolicy.firstAvailableStandard(List.of(occupied, isolation, disabled), Set.of(1L))).isEmpty();
    }

    @Test
    void suggestsIsolationOnlyForSickHorseInCompanyHubWithoutIsolation() {
        Location hub = new Location();
        hub.setLocationType(LocationType.HUB);
        HorseStay stay = new HorseStay();
        stay.setLocation(hub);
        assertThat(HubStallPolicy.suggestIsolation(stay, false)).isFalse();
        assertThat(HubStallPolicy.suggestIsolation(stay, true)).isTrue();
        stay.setHubStall(stall(1L, "I", HubStallKind.ISOLATION, true));
        assertThat(HubStallPolicy.suggestIsolation(stay, true)).isFalse();
        stay.setHubStall(null);
        stay.setOperatorParty(new Party());
        assertThat(HubStallPolicy.suggestIsolation(stay, true)).isFalse();
    }

    private static HubStall stall(Long id, String code, HubStallKind kind, boolean active) {
        HubStall stall = new HubStall();
        stall.setId(id);
        stall.setCode(code);
        stall.setStallKind(kind);
        stall.setActive(active);
        return stall;
    }
}
