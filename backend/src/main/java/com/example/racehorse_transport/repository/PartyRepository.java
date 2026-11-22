package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Party;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PartyRepository extends JpaRepository<Party, Long> {

    Optional<Party> findByUser_Id(Long userId);
}
