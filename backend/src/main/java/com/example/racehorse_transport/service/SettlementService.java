package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.payment.SettlementResponseDto;
import com.example.racehorse_transport.dto.payment.PaymentResponseDto;
import java.util.List;

public interface SettlementService {
    SettlementResponseDto settlement(Long bookingId);

    SettlementResponseDto complete(Long bookingId, String username);

    PaymentResponseDto createExtraCharge(Long bookingId, List<Long> costItemIds, String username);

    List<Long> autoCloseCandidates();

    boolean autoClose(Long bookingId);
}
