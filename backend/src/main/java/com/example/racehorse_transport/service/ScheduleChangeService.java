package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.booking.BookingResponseDto;
import com.example.racehorse_transport.dto.booking.ReportDocumentDelayRequestDto;
import com.example.racehorse_transport.policy.BookingCancellationPolicy.ScheduleCancellation;
import java.util.List;

public interface ScheduleChangeService {
    List<BookingResponseDto> missingDocuments();

    BookingResponseDto reportDocumentDelay(Long bookingId, String username, ReportDocumentDelayRequestDto request);

    ScheduleCancellation cancellationTerms(Long bookingId);

    List<Long> reminderCandidates();

    boolean remind(Long bookingId);
}
