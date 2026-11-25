package com.example.racehorse_transport.dto.health;

import com.example.racehorse_transport.entity.HubStall;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.enums.HubStallKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record HubStallCreateRequestDto(@NotBlank @Size(max = 40) String code, @NotNull HubStallKind stallKind) {
    public HubStall toEntity(Location hub) {
        HubStall stall = new HubStall();
        stall.setLocation(hub);
        stall.setCode(code.strip());
        stall.setStallKind(stallKind);
        return stall;
    }
}
