package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Otp;
import com.example.racehorse_transport.entity.enums.OtpPurpose;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface OtpRepository extends JpaRepository<Otp, Long> {

    Optional<Otp> findByEmailAndPurpose(String email, OtpPurpose purpose);

    // Khóa theo email trong transaction hiện tại: hai yêu cầu gửi hoặc nhập mã cùng lúc cho một email chạy lần lượt
    @Query(value = "SELECT pg_advisory_xact_lock(hashtext(:key))::text", nativeQuery = true)
    String lockEmail(@Param("key") String key);

    @Modifying
    @Query("DELETE FROM Otp o WHERE o.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
