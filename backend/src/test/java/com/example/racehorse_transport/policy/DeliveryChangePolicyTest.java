package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.*;

import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.entity.enums.*;
import com.example.racehorse_transport.exception.BookingRuleException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeliveryChangePolicyTest {
    @Test
    void rejectsBalanceAlreadyPaidAndRequiresDeposit() {
        var deposit = new Payment();
        deposit.setPaymentType(PaymentType.DEPOSIT);
        deposit.setStatus(PaymentStatus.PAID);
        deposit.setAmount(new BigDecimal("20"));
        var balance = new Payment();
        balance.setPaymentType(PaymentType.BALANCE);
        balance.setStatus(PaymentStatus.PAID);
        assertThat(DeliveryChangePolicy.paymentReason(List.of())).isEqualTo("NO_DEPOSIT");
        assertThat(DeliveryChangePolicy.paymentReason(List.of(deposit))).isNull();
        assertThat(DeliveryChangePolicy.paymentReason(List.of(deposit, balance))).isEqualTo("BALANCE_PAID");
    }

    @Test
    void guardsProvinceAndHubSeparately() {
        DeliveryChangePolicy.requireDestination("A", "A", 1L, 1L, 2, 3);
        assertThatThrownBy(() -> DeliveryChangePolicy.requireDestination("A", "B", 1L, 1L, 2, 3))
                .isInstanceOf(BookingRuleException.class).hasMessageContaining("cùng tỉnh");
        assertThatThrownBy(() -> DeliveryChangePolicy.requireDestination("A", "A", 1L, 2L, 2, 3))
                .isInstanceOf(BookingRuleException.class).hasMessageContaining("cùng hub");
    }

    @Test
    void rejectsPriceBelowPaidAndAStalePreview() {
        assertThat(DeliveryChangePolicy.requireBalance(new BigDecimal("34"), new BigDecimal("20"), new BigDecimal("34.00")))
                .isEqualByComparingTo("14");
        assertThatThrownBy(() -> DeliveryChangePolicy.requireBalance(new BigDecimal("19"), new BigDecimal("20"), null))
                .isInstanceOf(BookingRuleException.class).hasMessageContaining("thấp hơn");
        assertThatThrownBy(() -> DeliveryChangePolicy.requireBalance(new BigDecimal("34"), new BigDecimal("20"), new BigDecimal("33")))
                .isInstanceOf(BookingRuleException.class).hasMessageContaining("Giá đã thay đổi");
    }

    @Test
    void preservesFixedFeesAndIncidentFuel() {
        var line = new QuotationLine();
        line.setLineType(QuotationLineType.DELIVERY);
        line.setUnit(BillingUnit.PER_BOOKING.name());
        assertThat(DeliveryChangePolicy.replaceLine(line)).isFalse();
        line.setUnit(BillingUnit.PER_KM.name());
        assertThat(DeliveryChangePolicy.replaceLine(line)).isTrue();
        line.setLineType(QuotationLineType.FUEL);
        line.setHorseLeg(new HorseLeg());
        assertThat(DeliveryChangePolicy.replaceLine(line)).isFalse();
        line.setHorseLeg(null);
        line.setLineType(QuotationLineType.ADJUSTMENT);
        assertThat(DeliveryChangePolicy.replaceLine(line)).isFalse();
    }
}
