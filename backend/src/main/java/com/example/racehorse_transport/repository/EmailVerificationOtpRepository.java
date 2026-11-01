package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.EmailVerificationOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailVerificationOtpRepository extends JpaRepository<EmailVerificationOtp, Long> {
    Optional<EmailVerificationOtp> findByEmailIgnoreCase(String email);
}
