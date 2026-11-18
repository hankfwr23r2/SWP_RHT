package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.manager.QuoteLineDto;
import com.example.racehorse_transport.dto.settlement.ExpenseItemDto;
import com.example.racehorse_transport.entity.Booking;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface BillMailService {

    public enum Payment {
        DEPOSIT("Đặt cọc 30%", "Deposit (30%)"),
        BALANCE("Thanh toán 70% còn lại", "Remaining balance (70%)"),
        SETTLEMENT("Thanh toán quyết toán", "Final settlement");

        private final String vi;
        private final String en;

        Payment(String vi, String en) {
            this.vi = vi;
            this.en = en;
        }
        public String vi() { return vi; }
        public String en() { return en; }
    }

    void scheduleChangeRequired(Booking booking);

    void quoteIssued(Booking booking, List<QuoteLineDto> lines, BigDecimal total, BigDecimal deposit,
            BigDecimal balance, Instant expiresAt);

    void paymentReceived(Booking booking, Payment payment, BigDecimal amount, String transactionNo, Instant paidAt, String waybillNo);

    void settlementIssued(Booking booking, List<ExpenseItemDto> customerItems, BigDecimal total, Instant dueAt);
}
