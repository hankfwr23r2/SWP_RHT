package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.AppUser;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findByEmailIgnoreCase(String email);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT u FROM AppUser u WHERE u.id = :userId")
    Optional<AppUser> lockForExtraChargeReminder(Long userId);
}
