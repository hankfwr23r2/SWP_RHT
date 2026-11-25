package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.entity.enums.CostPayerType;
import com.example.racehorse_transport.entity.enums.IncidentCauseType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CostPayerPolicyTest {

    @Test
    void customerResponseDeadlineAndVeterinaryOverride() {
        var cost = new com.example.racehorse_transport.entity.CostItem();
        var deadline = java.time.Instant.parse("2030-01-01T01:15:00Z");
        cost.setCustomerResponseDueAt(deadline);
        cost.setCategory(com.example.racehorse_transport.entity.enums.CostCategory.REPAIR);
        assertThatThrownBy(() -> CostPayerPolicy.requireCostResponse(cost, deadline.minusSeconds(1), null))
                .hasMessageContaining("phản hồi");
        assertThatCode(() -> CostPayerPolicy.requireCostResponse(cost, deadline, null)).doesNotThrowAnyException();
        cost.setCustomerAgreed(false);
        assertThatThrownBy(() -> CostPayerPolicy.requireCostResponse(cost, deadline, "Urgent"))
                .hasMessageContaining("từ chối");
        cost.setCategory(com.example.racehorse_transport.entity.enums.CostCategory.VET);
        assertThatThrownBy(() -> CostPayerPolicy.requireCostResponse(cost, deadline, " "))
                .hasMessageContaining("từ chối");
        assertThatCode(() -> CostPayerPolicy.requireCostResponse(cost, deadline, "Urgent")).doesNotThrowAnyException();
    }

    @Test
    void payerFollowsTheCauseForAllSixCauses() {
        assertThat(CostPayerPolicy.payerFor(IncidentCauseType.COMPANY_FAULT)).isEqualTo(CostPayerType.COMPANY);
        assertThat(CostPayerPolicy.payerFor(IncidentCauseType.AGENT_FAULT)).isEqualTo(CostPayerType.COMPANY);
        assertThat(CostPayerPolicy.payerFor(IncidentCauseType.FORCE_MAJEURE)).isEqualTo(CostPayerType.COMPANY);
        assertThat(CostPayerPolicy.payerFor(IncidentCauseType.HORSE_CONDITION)).isEqualTo(CostPayerType.CUSTOMER);
        assertThat(CostPayerPolicy.payerFor(IncidentCauseType.CUSTOMER_DOCS)).isEqualTo(CostPayerType.CUSTOMER);
        assertThat(CostPayerPolicy.payerFor(IncidentCauseType.RECIPIENT_ABSENT)).isEqualTo(CostPayerType.CONSIGNEE);
    }

    @Test
    void customerSideCostNeedsABooking() {
        assertThatCode(() -> CostPayerPolicy.requireBookingForCustomerSide(CostPayerType.COMPANY, false)).doesNotThrowAnyException();
        assertThatCode(() -> CostPayerPolicy.requireBookingForCustomerSide(CostPayerType.CUSTOMER, true)).doesNotThrowAnyException();
        assertThatThrownBy(() -> CostPayerPolicy.requireBookingForCustomerSide(CostPayerType.CONSIGNEE, false)).hasMessageContaining("gắn với một đơn");
    }

    @Test
    void customerSideCostIsConsultedOrWithinTheEmergencyLimit() {
        BigDecimal limit = new BigDecimal("5000000");
        // Công ty chịu, hoặc đã hỏi khách: luôn được
        assertThatCode(() -> CostPayerPolicy.requireConsultedOrWithinEmergencyLimit(CostPayerType.COMPANY, false, new BigDecimal("9000000"), BigDecimal.ZERO, null)).doesNotThrowAnyException();
        assertThatCode(() -> CostPayerPolicy.requireConsultedOrWithinEmergencyLimit(CostPayerType.CUSTOMER, true, new BigDecimal("9000000"), BigDecimal.ZERO, null)).doesNotThrowAnyException();
        // Chưa hỏi: tổng các khoản chưa hỏi phải trong hạn mức
        assertThatCode(() -> CostPayerPolicy.requireConsultedOrWithinEmergencyLimit(CostPayerType.CUSTOMER, false, new BigDecimal("2000000"), new BigDecimal("3000000"), limit)).doesNotThrowAnyException();
        assertThatThrownBy(() -> CostPayerPolicy.requireConsultedOrWithinEmergencyLimit(CostPayerType.CUSTOMER, false, new BigDecimal("2000001"), new BigDecimal("3000000"), limit))
                .hasMessageContaining("vượt hạn mức");
        assertThatThrownBy(() -> CostPayerPolicy.requireConsultedOrWithinEmergencyLimit(CostPayerType.CONSIGNEE, false, BigDecimal.ONE, BigDecimal.ZERO, null))
                .hasMessageContaining("vượt hạn mức");
    }
}
