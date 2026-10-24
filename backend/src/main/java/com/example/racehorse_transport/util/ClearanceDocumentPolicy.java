package com.example.racehorse_transport.util;

import com.example.racehorse_transport.entity.Regulation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ClearanceDocumentPolicy {

    private static final Set<String> INTERNATIONAL_ONLY_TYPES = Set.of(
            "customs_declaration",
            "import_permit",
            "quarantine_cert",
            "ata_carnet",
            "commercial_invoice");

    private ClearanceDocumentPolicy() {}

    public static boolean isApplicable(Regulation regulation, boolean international) {
        if (international) return true;
        if (regulation.getLocationID() != null || isInternationalOnlyType(regulation.getDocumentType())) {
            return false;
        }
        String country = regulation.getCountry();
        return country == null || country.isBlank()
                || Set.of("vn", "vietnam", "việt nam").contains(country.trim().toLowerCase(Locale.ROOT));
    }

    public static boolean isInternationalOnlyType(Object type) {
        return type != null && INTERNATIONAL_ONLY_TYPES.contains(type.toString().trim().toLowerCase(Locale.ROOT));
    }

    public static Object forBooking(Object clearance, boolean international) {
        if (international || !(clearance instanceof Map<?, ?> clearanceMap)
                || !(clearanceMap.get("items") instanceof List<?> items)) {
            return clearance;
        }

        List<Object> applicableItems = new ArrayList<>();
        for (Object item : items) {
            if (!isInternationalOnlyItem(item)) {
                applicableItems.add(item);
            }
        }
        if (applicableItems.size() == items.size()) {
            return clearance;
        }

        Map<String, Object> filteredClearance = new HashMap<>();
        clearanceMap.forEach((key, value) -> {
            if (key instanceof String stringKey) filteredClearance.put(stringKey, value);
        });
        filteredClearance.put("items", applicableItems);
        return filteredClearance;
    }

    private static boolean isInternationalOnlyItem(Object item) {
        if (!(item instanceof Map<?, ?> itemMap)) return false;
        if (isInternationalOnlyType(itemMap.get("type"))) return true;
        return itemMap.get("gateLocationId") != null
                || (itemMap.get("gateName") != null && !itemMap.get("gateName").toString().isBlank());
    }
}
