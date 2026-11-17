package com.example.racehorse_transport.repository;
import com.example.racehorse_transport.entity.EmailOtp;
import com.example.racehorse_transport.entity.OtpPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;
public interface EmailOtpRepository extends JpaRepository<EmailOtp, Long> {
    Optional<EmailOtp> findByEmailAndPurpose(String email, OtpPurpose purpose);
    // One conditional DELETE: do not select stale entities and later delete a
    // token that a concurrent resend has already refreshed.
    @Modifying
    @Query(value = "DELETE FROM email_otp WHERE expires_at < :cutoff", nativeQuery = true)
    int deleteExpired(@Param("cutoff") Instant cutoff);
}
