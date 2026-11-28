package com.example.racehorse_transport.dto.party;

import com.example.racehorse_transport.entity.Party;

// Hãng bảo hiểm để chọn khi khai báo; không lộ thông tin liên hệ của đối tác
public record InsurerResponseDto(Long id, String displayName, String countryCode) {

    public static InsurerResponseDto from(Party party) {
        return new InsurerResponseDto(party.getId(), party.getDisplayName(), party.getCountry().getCode());
    }
}
