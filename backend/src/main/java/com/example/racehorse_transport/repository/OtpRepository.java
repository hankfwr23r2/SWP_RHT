package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Otp;
import com.example.racehorse_transport.entity.enums.OtpPurpose;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OtpRepository extends JpaRepository<Otp, Long> {

    Optional<Otp> findByEmailAndPurpose(String email, OtpPurpose purpose);
}
