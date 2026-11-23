package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.booking.BookingResponseDto;
import com.example.racehorse_transport.dto.settlement.*;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Payment;


public interface SettlementService {

    /**
     * 1. Kê khai chi phí phát sinh thực tế có chứng từ (Evidence-First - Flow 6, bước 2)
     */
    BookingResponseDto submitExpenses(String bookingIdentifier, SubmitExpensesRequestDto request, String username);

    /**
     * Manager approves the internal company expense review and closes the order.
     */
    SettlementResponseDto issueSettlement(String bookingIdentifier, IssueSettlementRequestDto request, String username);

    /**
     * Customer feedback after internal closure; independent of all payments.
     */
    BookingResponseDto submitRating(String bookingIdentifier, RatingDto request, String username);

    /**
     * Staff-only company expense review detail.
     */
    SettlementResponseDto getSettlementDetail(String bookingIdentifier);

    /**
     * Idempotently retire customer expense balances and normalize legacy cost ownership.
     */
    int migrateLegacySettlements();

    /**
     * Archive a legacy payment without issuing a new charge or releasing resources.
     */
    Booking confirmSettlementPaid(Payment payment, String actor);
}
