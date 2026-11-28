package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.booking.InsuranceDeclarationRequestDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingHorse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface InsuranceService {
    void recordPremiumRefund(Long bookingId, Long insuranceId,
            com.example.racehorse_transport.dto.booking.InsurancePremiumRefundRequestDto request, String username);

    void recordEvidence(Long bookingId, Long insuranceId, com.example.racehorse_transport.dto.booking.InsuranceEvidenceRequestDto request, String username);

    void confirm(Long bookingId, Long insuranceId, String username);

    void validateForQuoting(Booking booking, List<BookingHorse> activeHorses);

    void reopen(Long bookingId, Long insuranceId, String username, String reason);

    void requestRevision(Long bookingId, Long insuranceId, String username, String reason);

    void revise(Long bookingId, Long insuranceId, String username,
            com.example.racehorse_transport.dto.booking.PersonalInsuranceRevisionRequestDto request, MultipartFile file);

    void declare(BookingHorse horse, InsuranceDeclarationRequestDto request, LocalDate submittedOn, String ipAddress,
            MultipartFile file);
}
