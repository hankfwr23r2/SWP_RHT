package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Party;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartyRepository extends JpaRepository<Party, Long> {
}
