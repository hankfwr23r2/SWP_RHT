package com.example.racehorse_transport.dto.health;

import com.example.racehorse_transport.entity.HorseStay;
import com.example.racehorse_transport.entity.HubStall;
import jakarta.validation.constraints.NotNull;

public record HubStallChangeRequestDto(@NotNull Long hubStallId) {
    public void applyTo(HorseStay stay, HubStall stall) {
        stay.setHubStall(stall);
    }
}
