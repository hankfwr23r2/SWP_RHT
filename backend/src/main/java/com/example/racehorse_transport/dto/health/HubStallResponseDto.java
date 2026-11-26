package com.example.racehorse_transport.dto.health;

import com.example.racehorse_transport.entity.HubStall;
import com.example.racehorse_transport.entity.enums.HubStallKind;

public record HubStallResponseDto(Long id, String code, HubStallKind stallKind, boolean active, boolean occupied) {
    public static HubStallResponseDto from(HubStall stall, boolean occupied) {
        return new HubStallResponseDto(stall.getId(), stall.getCode(), stall.getStallKind(), stall.isActive(), occupied);
    }
}
