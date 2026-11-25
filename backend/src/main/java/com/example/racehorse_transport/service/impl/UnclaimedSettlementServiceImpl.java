package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.enums.*;
import com.example.racehorse_transport.policy.CostPayerPolicy;
import com.example.racehorse_transport.policy.UnclaimedSettlementPolicy;
import com.example.racehorse_transport.repository.CostItemRepository;
import com.example.racehorse_transport.repository.PaymentRepository;
import com.example.racehorse_transport.repository.QuotationRepository;
import com.example.racehorse_transport.service.RefundService;
import com.example.racehorse_transport.service.UnclaimedSettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UnclaimedSettlementServiceImpl implements UnclaimedSettlementService {
    private final QuotationRepository quotes;
    private final PaymentRepository payments;
    private final CostItemRepository costs;
    private final RefundService refunds;
    private final com.example.racehorse_transport.repository.HorseLegRepository legs;
    private final com.example.racehorse_transport.repository.AuditLogRepository auditLogs;
    private final com.example.racehorse_transport.service.AuditService audit;

    private static final String SETTLED = "UNUSED_DELIVERY_SETTLED";

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void settle(Long bookingId) {
        if (auditLogs.existsByTargetTableAndTargetIdAndActionAndCreatedAtGreaterThanEqual(
                "booking", bookingId, SETTLED, java.time.Instant.EPOCH)) return;
        var accepted = quotes.findByBooking_IdAndStatus(bookingId, QuotationStatus.ACCEPTED);
        if (accepted.isEmpty()) return;
        var quote = accepted.get();
        var entries = payments.findByBooking_IdOrderById(bookingId);
        var started = legs.findByBookingHorse_Booking_IdAndActualStartAtIsNotNull(bookingId);
        var credit = UnclaimedSettlementPolicy.unusedPaidDelivery(quote, quotes.findLinesForCancellation(quote.getId()),
                entries, started);
        credit = credit.subtract(costs.sumStablingDepositCredit(bookingId)).max(java.math.BigDecimal.ZERO);
        audit.record(null, SETTLED, "booking", bookingId, null, null);
        if (credit.signum() == 0) return;

        for (var fee : costs.findByBooking_IdOrderByIncurredAt(bookingId)) {
            if (credit.signum() == 0) break;
            var payment = fee.getExtraChargePayment();
            if (!CostPayerPolicy.isScheduledStablingFee(fee) || payment == null
                    || payment.getStatus() != PaymentStatus.PENDING || payment.getExtraChargeCosts().size() != 1
                    || payment.getDisputedAt() != null) continue;
            var applied = UnclaimedSettlementPolicy.credit(credit, payment.getAmount());
            fee.setStablingDeliveryCredit(applied);
            var remaining = payment.getAmount().subtract(applied);
            payment.setStatus(PaymentStatus.CANCELLED);
            if (remaining.signum() > 0) {
                var reissued = new Payment();
                reissued.setBooking(payment.getBooking());
                reissued.setQuotation(payment.getQuotation());
                reissued.setPaymentType(PaymentType.EXTRA_CHARGE);
                reissued.setPaymentMethod(payment.getPaymentMethod());
                reissued.setStatus(PaymentStatus.PENDING);
                reissued.setCurrencyCode(payment.getCurrencyCode());
                reissued.setAmount(remaining);
                reissued.setIssuedAt(payment.getIssuedAt());
                reissued.setDueAt(payment.getDueAt());
                fee.setExtraChargePayment(payments.save(reissued));
            }
            credit = credit.subtract(applied);
        }
        if (credit.signum() > 0) refunds.createPending(bookingId, quote.getCurrencyCode(), credit);
    }
}
