package com.example.racehorse_transport.dto.specialist;

import com.example.racehorse_transport.dto.booking.BookingSummaryResponseDto;
import java.time.LocalDate;
import java.util.List;

public record SpecialistWorkResponseDto(BookingSummaryResponseDto booking, List<String> horseNames,
        Verification verification, Legal legal, Insurance insurance, LocalDate documentsDueOn,
        long arranged, long personal, long uninsured) {
    public enum Verification { TO_REVIEW, WAITING_CUSTOMER, REVIEWED, AWAITING_INTAKE }
    public enum Legal { TO_PREPARE, IN_PROGRESS, COMPLETE }
    public enum Insurance { TO_PROCESS, WAITING_CUSTOMER, CONFIRMED }
}
