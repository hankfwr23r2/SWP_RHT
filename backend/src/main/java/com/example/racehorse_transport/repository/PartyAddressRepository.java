package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.PartyAddress;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PartyAddressRepository extends JpaRepository<PartyAddress, Long> {

    List<PartyAddress> findByParty_IdOrderById(Long partyId);
}
