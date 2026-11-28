package com.example.racehorse_transport.entity;

import com.example.racehorse_transport.entity.enums.PartyRoleType;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

// Khóa của party_role.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Embeddable
public class PartyRoleId implements Serializable {

    @Column(name = "party_id", nullable = false)
    private Long partyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_type", length = 30, nullable = false)
    private PartyRoleType roleType;
}
