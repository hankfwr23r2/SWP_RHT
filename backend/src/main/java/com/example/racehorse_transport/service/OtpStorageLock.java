package com.example.racehorse_transport.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
/** Locks issuance/consumption by account, including before any OTP row exists.
 * Sharing the lock across purposes also protects concurrent updates to users. */

@Component
@RequiredArgsConstructor
public class OtpStorageLock {
    private final JdbcTemplate jdbc;
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(String email) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> { }, "email-otp:" + email);
    }
}
