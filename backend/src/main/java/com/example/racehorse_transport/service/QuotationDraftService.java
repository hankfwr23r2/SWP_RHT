package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.quotation.QuotationResponseDto;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.Quotation;
import com.example.racehorse_transport.entity.QuotationLine;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface QuotationDraftService {
    QuotationResponseDto create(Long bookingId);

    List<Instant> validateInsurance(Quotation quotation, Instant now);

    void setTotals(Quotation quotation, List<QuotationLine> lines);

    Optional<Quotation> replaceAfterExclusion(BookingHorse excluded);
}
