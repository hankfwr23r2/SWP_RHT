package com.example.racehorse_transport.util;

import com.example.racehorse_transport.dto.booking.DocumentRequirementSnapshotDto;
import com.example.racehorse_transport.entity.DocumentRequirement;
import com.example.racehorse_transport.entity.Location;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ClearanceDocumentPolicy {

    private static final Set<String> INTERNATIONAL_ONLY_TYPES = Set.of(
            "customs_declaration",
            "import_permit",
            "quarantine_cert",
            "ata_carnet",
            "commercial_invoice");

    private ClearanceDocumentPolicy() {}

    /**
     * Dựng danh sách mục thông quan của một đơn.
     *
     * @param activeClearance requirement nhóm CLEARANCE đang ACTIVE (gồm cả dòng chung và dòng theo cửa khẩu)
     * @param borderGate      cửa khẩu của đơn; null nếu chưa xác định
     * @param snapshot        requirement đã chốt lúc đặt đơn; null với đơn cũ chưa có snapshot
     */
    public static List<Map<String, Object>> buildItems(List<DocumentRequirement> activeClearance, boolean international,
            Location borderGate, String destinationCountry, List<DocumentRequirementSnapshotDto> snapshot) {
        List<DocumentRequirement> gateRows = international
                ? activeClearance.stream()
                        .filter(requirement -> requirement.getLocation() != null)
                        .filter(requirement -> borderGate != null
                                ? Objects.equals(borderGate.getId(), requirement.getLocation().getId())
                                : isForDestinationCountry(requirement, destinationCountry))
                        .toList()
                : List.of();
        List<Map<String, Object>> items = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        if (snapshot != null) {
            // Tên/mô tả theo snapshot đã chốt với khách; dòng cửa khẩu chỉ bổ sung thông tin cửa khẩu
            for (DocumentRequirementSnapshotDto requirement : snapshot) {
                String type = requirement.getDocumentType();
                if (!"CLEARANCE".equals(requirement.getDocumentGroup()) || !seen.add(type)) continue;
                List<DocumentRequirement> gates = gateRows.stream().filter(gate -> type.equals(gate.getDocumentType())).toList();
                if (gates.isEmpty()) {
                    items.add(item(type, requirement.getDocumentName(), requirement.getDescription(), null));
                }
                for (DocumentRequirement gate : gates) {
                    items.add(item(type, requirement.getDocumentName(),
                            requirement.getDescription() != null ? requirement.getDescription() : gate.getDescription(),
                            gate.getLocation()));
                }
            }
            return items;
        }

        // Đơn cũ không có snapshot: lấy cấu hình hiện hành
        String tripType = international ? "INTERNATIONAL" : "DOMESTIC";
        for (DocumentRequirement general : activeClearance) {
            if (general.getLocation() != null || !general.isRequired()
                    || !DocumentRequirementPolicy.applies(general, tripType, destinationCountry)
                    || (!international && isInternationalOnlyType(general.getDocumentType()))
                    || !seen.add(general.getDocumentType())) continue;
            List<DocumentRequirement> gates = gateRows.stream()
                    .filter(gate -> general.getDocumentType().equals(gate.getDocumentType())).toList();
            if (gates.isEmpty()) {
                items.add(item(general.getDocumentType(), general.getDocumentName(), general.getDescription(), null));
            }
            for (DocumentRequirement gate : gates) {
                items.add(item(gate.getDocumentType(), gate.getDocumentName(), gate.getDescription(), gate.getLocation()));
            }
        }
        for (DocumentRequirement gate : gateRows) {
            if (gate.isRequired() && !seen.contains(gate.getDocumentType())) {
                items.add(item(gate.getDocumentType(), gate.getDocumentName(), gate.getDescription(), gate.getLocation()));
            }
        }
        return items;
    }

    static boolean isForDestinationCountry(DocumentRequirement requirement, String country) {
        if (requirement.getLocation() == null || country == null || country.isBlank()) {
            return true;
        }
        String destination = requirement.getLocation().getDestinationCountry();
        if (destination == null || destination.isBlank()) {
            destination = requirement.getCountry();
        }
        return destination == null || destination.isBlank() || "ALL".equalsIgnoreCase(destination)
                || DocumentRequirementPolicy.normalizeCountry(destination)
                        .equals(DocumentRequirementPolicy.normalizeCountry(country));
    }

    private static Map<String, Object> item(String type, String label, String hint, Location gate) {
        Map<String, Object> item = new HashMap<>();
        item.put("type", type);
        item.put("label", label);
        item.put("hint", hint);
        item.put("note", "");
        item.put("status", "todo");
        item.put("photos", new ArrayList<String>());
        if (gate != null) {
            item.put("gateName", gate.getLocationName());
            item.put("gateLocationId", gate.getId());
        }
        return item;
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
