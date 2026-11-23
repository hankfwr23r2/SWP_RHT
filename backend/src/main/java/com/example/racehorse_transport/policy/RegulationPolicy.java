package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.CountryRule;
import com.example.racehorse_transport.entity.DocumentRequirement;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.entity.enums.LocationType;
import com.example.racehorse_transport.entity.enums.RuleDirection;
import com.example.racehorse_transport.exception.RegulationRuleException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RegulationPolicy {

    private static final String VIETNAM = "VN";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private RegulationPolicy() {
    }

    public static String partnerCountry(BookingType type, String pickupCountry, String deliveryCountry) {
        return switch (type) {
            case LOCAL -> null;
            case INTERPROVINCE -> VIETNAM;
            case EXPORT -> deliveryCountry;
            case IMPORT -> pickupCountry;
        };
    }

    public static boolean effectiveOn(CountryRule rule, LocalDate pickupDate) {
        return !pickupDate.isBefore(rule.getValidFrom())
                && (rule.getValidTo() == null || !pickupDate.isAfter(rule.getValidTo()));
    }

    public static void validateRule(String countryCode, RuleDirection direction, LocalDate from, LocalDate to) {
        if ((direction == RuleDirection.INTERPROVINCE) != VIETNAM.equals(countryCode)) {
            throw new RegulationRuleException("countryCode", "Quốc gia không phù hợp với chiều vận chuyển.");
        }
        if (to != null && to.isBefore(from)) {
            throw new RegulationRuleException("validTo", "Ngày hết hiệu lực phải từ ngày bắt đầu trở đi.");
        }
    }

    public static void validateGate(Location location) {
        if (location.getLocationType() != LocationType.BORDER_GATE) {
            throw new RegulationRuleException("gateLocationId", "Địa điểm được chọn phải là cửa khẩu.");
        }
    }

    public static long leadDays(CountryRule rule) {
        return (long) rule.getPermitLeadDays() + rule.getCertificateLeadDays() + rule.getQuarantineDays();
    }

    public static boolean feasible(long leadDays, Instant now, Instant bookingCutoffAt) {
        LocalDate today = now.atZone(BUSINESS_ZONE).toLocalDate();
        LocalDate cutoffDate = bookingCutoffAt.atZone(BUSINESS_ZONE).toLocalDate();
        return bookingCutoffAt.isAfter(now) && !cutoffDate.isBefore(today.plusDays(leadDays));
    }

    public static List<DocumentRequirement> requirementsForGate(List<DocumentRequirement> requirements, Long gateId) {
        Map<Long, DocumentRequirement> selectedByType = new LinkedHashMap<>();
        for (DocumentRequirement requirement : requirements) {
            Long typeId = requirement.getDocumentType().getId();
            Location gate = requirement.getGateLocation();
            if (gate == null) {
                selectedByType.putIfAbsent(typeId, requirement);
            } else if (gate.getId().equals(gateId)) {
                selectedByType.put(typeId, requirement);
            }
        }
        return List.copyOf(selectedByType.values());
    }
}
