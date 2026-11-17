package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.booking.BookingResponse;
import com.example.racehorse_transport.dto.settlement.*;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Payment;


public interface SettlementService {

    /**
     * 1. Kê khai chi phí phát sinh thực tế có chứng từ (Evidence-First - Flow 6, bước 2)
     */
    BookingResponse submitExpenses(String bookingIdentifier, SubmitExpensesRequest request, String username);

    /**
     * Manager approves the internal company expense review and closes the order.
     */
    SettlementResponse issueSettlement(String bookingIdentifier, IssueSettlementRequest request, String username);

    /**
     * Customer feedback after internal closure; independent of all payments.
     */
    BookingResponse submitRating(String bookingIdentifier, RatingDto request, String username);

    /**
     * Staff-only company expense review detail.
     */
    SettlementResponse getSettlementDetail(String bookingIdentifier);

    /**
     * Idempotently retire customer expense balances and normalize legacy cost ownership.
     */
    int migrateLegacySettlements();

    /**
     * Archive a legacy payment without issuing a new charge or releasing resources.
     */
    Booking confirmSettlementPaid(Payment payment, String actor);
}
