package com.example.racehorse_transport.dto.incident;

import com.example.racehorse_transport.entity.CostItem;
import com.example.racehorse_transport.entity.enums.CostCategory;
import com.example.racehorse_transport.entity.enums.CostPayerType;
import java.math.BigDecimal;
import java.time.Instant;

public record CostItemResponseDto(
        Long id, CostCategory category, String description, BigDecimal amount, String currencyCode, CostPayerType payerType,
        Long incidentId, String bookingCode, Boolean customerConsulted, Instant incurredAt,
        String recordedByName, String approvedByName, Instant approvedAt, Long extraChargePaymentId) {

    public static CostItemResponseDto from(CostItem cost) {
        return new CostItemResponseDto(cost.getId(), cost.getCategory(), cost.getDescription(), cost.getAmount(), cost.getCurrencyCode(),
                cost.getPayerType(), cost.getIncident() == null ? null : cost.getIncident().getId(),
                cost.getBooking() == null ? null : cost.getBooking().getCode(), cost.getCustomerConsulted(), cost.getIncurredAt(),
                cost.getRecordedBy().getFullName(), cost.getApprovedBy() == null ? null : cost.getApprovedBy().getFullName(),
                cost.getApprovedAt(), cost.getExtraChargePayment() == null ? null : cost.getExtraChargePayment().getId());
    }
}
