package com.example.racehorse_transport.dto.payment;

import com.example.racehorse_transport.dto.incident.CostItemResponseDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import java.math.BigDecimal;
import java.util.List;

public record SettlementResponseDto(Long bookingId, BookingStatus status, String currencyCode,
        BigDecimal quotationTotal, BigDecimal approvedCustomerCosts, BigDecimal outstanding,
        boolean hasOpenIncident, boolean canClose, List<Long> chargeableCostIds, List<CostItemResponseDto> costs, List<PaymentResponseDto> payments) {

    public static SettlementResponseDto from(Booking booking, String currency, BigDecimal quotationTotal,
            BigDecimal approvedCosts, BigDecimal outstanding, boolean hasOpenIncident, boolean canClose, List<Long> chargeableCostIds,
            List<CostItemResponseDto> costs, List<PaymentResponseDto> payments) {
        return new SettlementResponseDto(booking.getId(), booking.getStatus(), currency, quotationTotal,
                approvedCosts, outstanding, hasOpenIncident, canClose, chargeableCostIds, costs, payments);
    }
}
