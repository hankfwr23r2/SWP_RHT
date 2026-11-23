package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.PartyRole;
import com.example.racehorse_transport.entity.PartyRoleId;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PartyRoleRepository extends JpaRepository<PartyRole, PartyRoleId> {

    List<PartyRole> findById_PartyId(Long partyId);
}
