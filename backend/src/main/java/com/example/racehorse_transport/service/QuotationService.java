package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.quotation.*;
import java.util.List;

public interface QuotationService {
    QuotationResponseDto createDraft(Long bookingId);

    List<QuotationResponseDto> quotations(Long bookingId, String username, boolean manager);

    QuotationResponseDto adjust(Long quotationId, QuotationAdjustmentRequestDto request);

    QuotationResponseDto send(Long quotationId, String username);

    QuotationResponseDto accept(Long quotationId, String username);

    QuotationResponseDto reject(Long quotationId, QuotationRejectRequestDto request, String username);

    List<Long> expirationCandidates();

    void expire(Long quotationId);
}
