package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.HorseStay;
import com.example.racehorse_transport.entity.HubStall;
import com.example.racehorse_transport.entity.enums.HubStallKind;
import com.example.racehorse_transport.entity.enums.LocationType;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class HubStallPolicy {
    private HubStallPolicy() {}

    public static boolean isCompanyHubStay(HorseStay stay) {
        return stay.getLocation().getLocationType() == LocationType.HUB && stay.getOperatorParty() == null;
    }

    public static boolean isOpen(HorseStay stay) {
        return stay.getActualStartAt() != null && stay.getActualEndAt() == null;
    }

    public static Optional<HubStall> firstAvailableStandard(List<HubStall> stalls, Set<Long> occupied) {
        return stalls.stream()
                .filter(HubStall::isActive)
                .filter(stall -> stall.getStallKind() == HubStallKind.STANDARD && !occupied.contains(stall.getId()))
                .min(Comparator.comparing(HubStall::getCode));
    }

    public static boolean suggestIsolation(HorseStay stay, boolean sick) {
        return sick && isCompanyHubStay(stay)
                && (stay.getHubStall() == null || stay.getHubStall().getStallKind() != HubStallKind.ISOLATION);
    }
}
