package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.CostItem;
import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.entity.enums.IncidentType;
import com.example.racehorse_transport.entity.enums.CostCategory;
import com.example.racehorse_transport.entity.enums.HorseStayType;
import com.example.racehorse_transport.entity.enums.CostPayerType;
import com.example.racehorse_transport.entity.enums.IncidentCauseType;
import com.example.racehorse_transport.exception.DispatchRuleException;
import java.math.BigDecimal;

// Ai chịu chi phí phát sinh (docs/NGHIEP_VU_THEO_59_BANG.md mục GĐ9), hàm thuần
public final class CostPayerPolicy {

    private CostPayerPolicy() {}

    // Bên chịu theo nguyên nhân sự cố. Đại lý lỗi thì công ty chịu trước rồi đòi lại theo hợp đồng riêng
    public static CostPayerType payerFor(IncidentCauseType cause) {
        return switch (cause) {
            case COMPANY_FAULT, FORCE_MAJEURE -> CostPayerType.COMPANY;
            case HORSE_CONDITION, CUSTOMER_DOCS -> CostPayerType.CUSTOMER;
            case RECIPIENT_ABSENT -> CostPayerType.CONSIGNEE;
        };
    }

    public static IncidentCauseType effectiveCause(Incident incident) {
        return incident.getConfirmedCauseType() == null ? incident.getCauseType() : incident.getConfirmedCauseType();
    }

    public static boolean causeConfirmed(Incident incident) {
        return incident.getCauseConfirmedAt() != null || incident.getIncidentType() == IncidentType.HANDOVER_TIMEOUT;
    }

    public static void requireRecordableCategory(CostCategory category) {
        if (category == CostCategory.WAITING) {
            throw new DispatchRuleException("category", "Không thu phí chờ.");
        }
        if (category == CostCategory.REDELIVERY || category == CostCategory.PLANNED_STABLING) {
            throw new DispatchRuleException("category", "Khoản phí này do hệ thống tính theo biểu giá.");
        }
    }

    public static boolean customerSide(CostPayerType payer) {
        return payer == CostPayerType.CUSTOMER || payer == CostPayerType.CONSIGNEE;
    }

    public static boolean isScheduledStablingFee(CostItem cost) {
        return cost.getCategory() == CostCategory.STABLING
                && cost.getHorseStay() != null && cost.getHorseStay().getStayType() == HorseStayType.STABLING;
    }

    public static boolean isAutomaticTransportFee(CostItem cost) {
        return isScheduledStablingFee(cost) || cost.getCategory() == CostCategory.REDELIVERY
                || cost.getCategory() == CostCategory.PLANNED_STABLING;
    }

    public static boolean countsAgainstEmergencyLimit(CostItem cost) {
        return customerSide(cost.getPayerType()) && !Boolean.TRUE.equals(cost.getCustomerConsulted())
                && !isAutomaticTransportFee(cost);
    }

    public static void requireCostResponse(CostItem cost, java.time.Instant now, String overrideReason) {
        if (Boolean.FALSE.equals(cost.getCustomerAgreed())) {
            if (cost.getCategory() != CostCategory.VET || overrideReason == null || overrideReason.isBlank()) {
                throw new DispatchRuleException("emergencyOverrideReason", "Khách đã từ chối khoản chi phí này.", true);
            }
        } else if (cost.getCustomerResponseDueAt() == null || now.isBefore(cost.getCustomerResponseDueAt())) {
            throw new DispatchRuleException("customerResponse", "Vẫn còn thời gian chờ phản hồi của khách.", true);
        }
    }

    // Khoản khách chịu phải gắn với một đơn
    public static void requireBookingForCustomerSide(CostPayerType payer, boolean hasBooking) {
        if (payer != CostPayerType.COMPANY && !hasBooking) {
            throw new DispatchRuleException("bookingId", "Chi phí do khách hoặc người nhận chịu phải gắn với một đơn.");
        }
    }

    // Khoản khách chịu phải hỏi khách trước khi chi, trừ khi khẩn cấp và tổng các khoản chưa hỏi vẫn trong hạn mức chi khẩn cấp của đơn
    public static void requireConsultedOrWithinEmergencyLimit(CostPayerType payer, boolean consulted, BigDecimal amount,
                                                              BigDecimal alreadyApprovedWithoutConsulting, BigDecimal emergencyLimit) {
        if (!customerSide(payer) || consulted) return;
        BigDecimal total = alreadyApprovedWithoutConsulting.add(amount);
        if (emergencyLimit == null || total.compareTo(emergencyLimit) > 0) {
            throw new DispatchRuleException("customerConsulted",
                    "Khoản này do khách chịu, chưa hỏi ý kiến khách và vượt hạn mức chi khẩn cấp của đơn.", true);
        }
    }
}
