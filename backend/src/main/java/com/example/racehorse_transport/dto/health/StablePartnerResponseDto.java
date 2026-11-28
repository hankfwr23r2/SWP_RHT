package com.example.racehorse_transport.dto.health;

import com.example.racehorse_transport.entity.Party;

// Đối tác chuồng gửi qua đêm (vai trò STABLE còn hiệu lực)
public record StablePartnerResponseDto(Long id, String displayName, String address, String phone) {

    public static StablePartnerResponseDto from(Party party) {
        return new StablePartnerResponseDto(party.getId(), party.getDisplayName(), party.getAddress(), party.getPhone());
    }
}
