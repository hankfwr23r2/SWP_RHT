package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.entity.BillableService;
import com.example.racehorse_transport.entity.HorseInsurance;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.PriceItem;
import com.example.racehorse_transport.entity.PriceList;
import com.example.racehorse_transport.entity.Quotation;
import com.example.racehorse_transport.entity.QuotationLine;
import com.example.racehorse_transport.entity.enums.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PickupMissPricingPolicyTest {
    @Test
    void figuresFromSection85UseTheQuotedRatesAndActualDeposit() {
        var list = new PriceList();
        list.setMarginRate(BigDecimal.ZERO);
        list.setFuelBufferRate(BigDecimal.ZERO);
        var pickup = item(1L, ServiceLayer.PICKUP, "26250");
        var fuel = item(2L, ServiceLayer.FUEL, "9450");
        var charges = PricingPolicy.roadCharges(list, List.of(pickup, fuel),
                List.of(pickup.getBillableService(), fuel.getBillableService()), ServiceLayer.PICKUP,
                null, 1L, money("54"));
        assertThat(charges.getFirst().amount()).isEqualByComparingTo("1417500");
        assertThat(charges.getLast().amount()).isEqualByComparingTo("510300");
        var retry = charges.stream().map(PricingPolicy.Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(retry).isEqualByComparingTo("1927800");

        var deposit = new Payment();
        deposit.setPaymentType(PaymentType.DEPOSIT);
        deposit.setStatus(PaymentStatus.PAID);
        deposit.setAmount(money("8761000"));
        deposit.setCurrencyCode("VND");
        var quote = new Quotation();
        quote.setCurrencyCode("VND");
        quote.setTotalAmount(money("28733450"));
        assertThat(PaymentPolicy.outstanding(quote, List.of(deposit))).isEqualByComparingTo("19972450");
        quote.setTotalAmount(quote.getTotalAmount().add(retry));
        assertThat(quote.getTotalAmount()).isEqualByComparingTo("30661250");
        assertThat(PaymentPolicy.outstanding(quote, List.of(deposit))).isEqualByComparingTo("21900250");
        quote.setTotalAmount(money("16133450"));
        assertThat(PaymentPolicy.outstanding(quote, List.of(deposit))).isEqualByComparingTo("7372450");
        assertThat(BookingCancellationPolicy.pickupMissAmounts(true, quote, List.of(deposit)).forfeited())
                .isEqualByComparingTo("8761000");
        assertThat(BookingCancellationPolicy.pickupMissAmounts(false, quote, List.of(deposit)).refundable())
                .isEqualByComparingTo("8761000");
    }

    @Test
    void customerKeepsCompletedServicesAndInsuranceAndDepositFloor() {
        var row = new QuotationLine();
        row.setLineType(QuotationLineType.SERVICE);
        assertThat(PickupMissPricingPolicy.retainExcludedLine(row, true, false)).isFalse();
        assertThat(PickupMissPricingPolicy.retainExcludedLine(row, true, true)).isTrue();
        assertThat(PickupMissPricingPolicy.retainExcludedLine(row, false, true)).isFalse();
        row.setLineType(QuotationLineType.INSURANCE);
        assertThat(PickupMissPricingPolicy.retainExcludedLine(row, true, false)).isTrue();
        assertThat(PickupMissPricingPolicy.retainExcludedLine(row, false, false)).isFalse();
        assertThat(PickupMissPricingPolicy.depositFloorAdjustment(money("7000000"), money("8761000")))
                .isEqualByComparingTo("1761000");
        assertThat(PickupMissPricingPolicy.depositFloorAdjustment(money("16133450"), money("8761000"))).isZero();
    }

    private static PriceItem item(Long id, ServiceLayer layer, String unitPrice) {
        var service = new BillableService();
        service.setId(id);
        service.setLayer(layer);
        service.setBillingUnit(BillingUnit.PER_KM);
        service.setPassThrough(false);
        var item = new PriceItem();
        item.setId(id);
        item.setBillableService(service);
        item.setUnitPrice(money(unitPrice));
        return item;
    }

    @Test
    void confirmedInsurerCreditIsCumulativeAndCannotExceedThePremium() {
        var insurance = new HorseInsurance();
        insurance.setCoverageOption(InsuranceCoverageOption.ARRANGED);
        insurance.setStatus(HorseInsuranceStatus.CONFIRMED);
        insurance.setPremiumAmount(money("1500000"));
        assertThat(PickupMissPricingPolicy.newPremiumRefund(insurance, money("500000"))).isTrue();
        insurance.setRefundedPremiumAmount(money("500000"));
        assertThat(PickupMissPricingPolicy.newPremiumRefund(insurance, money("500000"))).isFalse();
        assertThat(PickupMissPricingPolicy.remainingPremium(insurance, money("1500000"))).isEqualByComparingTo("1000000");
        assertThatThrownBy(() -> PickupMissPricingPolicy.newPremiumRefund(insurance, money("499999")))
                .isInstanceOf(BookingRuleException.class);
        assertThatThrownBy(() -> PickupMissPricingPolicy.newPremiumRefund(insurance, money("1500001")))
                .isInstanceOf(BookingRuleException.class);
    }

    private static BigDecimal money(String amount) {
        return new BigDecimal(amount);
    }
}
