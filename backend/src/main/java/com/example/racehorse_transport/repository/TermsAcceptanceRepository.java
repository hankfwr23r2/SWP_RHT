package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.TermsAcceptance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TermsAcceptanceRepository extends JpaRepository<TermsAcceptance, Integer> {
}
