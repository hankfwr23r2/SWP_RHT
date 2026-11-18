package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.User;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public interface AdminAuditService {

    public static final Set<String> TARGETS = Set.of("USER", "SYSTEM_CONFIG", "SERVICE", "ROUTE", "LOCATION", "DOCUMENT_REQUIREMENT", "HORSE_TYPE");

    public static final Map<String, java.util.List<String>> ACTIONS = Map.of(
            "HORSE_TYPE", java.util.List.of("CREATE_HORSE_TYPE", "UPDATE_HORSE_TYPE", "DELETE_HORSE_TYPE", "UPDATE_INSURED_VALUE"),
            "USER", java.util.List.of("CREATE_ACCOUNT", "UPDATE_ACCOUNT", "LOCK_ACCOUNT", "UNLOCK_ACCOUNT", "RESET_PASSWORD", "CHANGE_PASSWORD"),
            "SYSTEM_CONFIG", java.util.List.of("UPDATE_CONFIG"),
            "SERVICE", java.util.List.of("CREATE_SERVICE", "UPDATE_SERVICE", "CHANGE_SERVICE_STATUS"),
            "ROUTE", java.util.List.of("CREATE_ROUTE", "UPDATE_ROUTE", "DEACTIVATE_ROUTE"),
            "LOCATION", java.util.List.of("CREATE_LOCATION", "UPDATE_LOCATION", "CHANGE_LOCATION_STATUS"),
            "DOCUMENT_REQUIREMENT", java.util.List.of("CREATE_DOCUMENT_REQUIREMENT", "UPDATE_DOCUMENT_REQUIREMENT", "CHANGE_DOCUMENT_REQUIREMENT_STATUS"));

    public record Actor(User user, String name, String email, String role) {}

    public static Map<String, Object> values(Object... pairs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) values.put((String) pairs[i], pairs[i + 1]);
        return values;
    }

    Actor actor();

    void record(String action, String target, Object key, String label, String description,
            Map<String, ?> before, Map<String, ?> after);

    void recordAs(Actor actor, String action, String target, Object key, String label, String description,
            Map<String, ?> before, Map<String, ?> after);
}
