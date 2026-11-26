package com.example.racehorse_transport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.entity.enums.*;
import com.example.racehorse_transport.repository.*;
import com.example.racehorse_transport.service.impl.UnclaimedSettlementServiceImpl;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UnclaimedSettlementServiceTest {
    private final QuotationRepository quotes = mock(QuotationRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final CostItemRepository costs = mock(CostItemRepository.class);
    private final RefundService refunds = mock(RefundService.class);
    private final HorseLegRepository legs = mock(HorseLegRepository.class);
    private final AuditLogRepository logs = mock(AuditLogRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final UnclaimedSettlementService settlement = new UnclaimedSettlementServiceImpl(quotes, payments, costs, refunds,
            legs, logs, audit);

    @Test
    void creditsUnpaidHoldingFeeAndRefundsUnusedRemainder() {
        var quote = new Quotation();
        quote.setId(2L);
        quote.setCurrencyCode("VND");
        quote.setTotalAmount(new BigDecimal("1000"));
        var delivery = new QuotationLine();
        delivery.setLineType(QuotationLineType.DELIVERY);
        delivery.setAmount(new BigDecimal("400"));
        var transport = new Payment();
        transport.setPaymentType(PaymentType.BALANCE);
        transport.setStatus(PaymentStatus.PAID);
        transport.setCurrencyCode("VND");
        transport.setAmount(new BigDecimal("1000"));
        var holdingCharge = new Payment();
        holdingCharge.setPaymentType(PaymentType.EXTRA_CHARGE);
        holdingCharge.setStatus(PaymentStatus.PENDING);
        holdingCharge.setAmount(new BigDecimal("250"));
        var fee = new CostItem();
        fee.setCategory(CostCategory.STABLING);
        var stay = new HorseStay();
        stay.setStayType(HorseStayType.STABLING);
        fee.setHorseStay(stay);
        fee.setExtraChargePayment(holdingCharge);
        holdingCharge.getExtraChargeCosts().add(fee);

        when(quotes.findByBooking_IdAndStatus(1L, QuotationStatus.ACCEPTED)).thenReturn(Optional.of(quote));
        when(quotes.findLinesForCancellation(2L)).thenReturn(List.of(delivery));
        when(payments.findByBooking_IdOrderById(1L)).thenReturn(List.of(transport, holdingCharge));
        when(costs.sumStablingDepositCredit(1L)).thenReturn(BigDecimal.ZERO);
        when(costs.findByBooking_IdOrderByIncurredAt(1L)).thenReturn(List.of(fee));

        settlement.settle(1L);

        assertThat(fee.getStablingDeliveryCredit()).isEqualByComparingTo("250");
        assertThat(holdingCharge.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(refunds).createPending(1L, "VND", new BigDecimal("150"));
    }

    @Test
    void repeatedSettlementDoesNotRefundOrIssueChargesAgain() {
        when(logs.existsByTargetTableAndTargetIdAndActionAndCreatedAtGreaterThanEqual(
                "booking", 1L, "UNUSED_DELIVERY_SETTLED", Instant.EPOCH)).thenReturn(true);

        settlement.settle(1L);

        verifyNoInteractions(quotes, payments, costs, refunds, audit);
    }
}
