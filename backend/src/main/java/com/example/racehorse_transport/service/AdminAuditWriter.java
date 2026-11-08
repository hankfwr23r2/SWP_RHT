package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.SystemLog;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.SystemLogRepository;
import com.example.racehorse_transport.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Explicit, allow-listed snapshots only; never serialize a User or request containing credentials. */
@Service
@RequiredArgsConstructor
public class AdminAuditWriter {
    public static final Set<String> TARGETS = Set.of("USER", "SYSTEM_CONFIG", "SERVICE", "ROUTE", "LOCATION", "DOCUMENT_REQUIREMENT", "HORSE_TYPE");
    public static final Map<String, java.util.List<String>> ACTIONS = Map.of(
            "HORSE_TYPE", java.util.List.of("CREATE_HORSE_TYPE", "UPDATE_HORSE_TYPE", "DELETE_HORSE_TYPE", "UPDATE_INSURED_VALUE"),
            "USER", java.util.List.of("CREATE_ACCOUNT", "UPDATE_ACCOUNT", "LOCK_ACCOUNT", "UNLOCK_ACCOUNT", "RESET_PASSWORD", "CHANGE_PASSWORD"),
            "SYSTEM_CONFIG", java.util.List.of("UPDATE_CONFIG"),
            "SERVICE", java.util.List.of("CREATE_SERVICE", "UPDATE_SERVICE", "CHANGE_SERVICE_STATUS"),
            "ROUTE", java.util.List.of("CREATE_ROUTE", "UPDATE_ROUTE", "DEACTIVATE_ROUTE"),
            "LOCATION", java.util.List.of("CREATE_LOCATION", "UPDATE_LOCATION", "CHANGE_LOCATION_STATUS"),
            "DOCUMENT_REQUIREMENT", java.util.List.of("CREATE_DOCUMENT_REQUIREMENT", "UPDATE_DOCUMENT_REQUIREMENT", "CHANGE_DOCUMENT_REQUIREMENT_STATUS"));
    private static final Set<String> FIELDS = Set.of(
            "name", "email", "phone", "role", "staffCode", "status", "value", "unit", "group",
            "serviceName", "serviceCode", "billingUnit", "description", "categoryId", "price",
            "routeName", "estimatedTime", "distance", "stops", "locationCode", "locationType", "locationName",
            "address", "city", "province", "country", "latitude", "longitude", "openingMinutes",
            "closingMinutes", "contactPhone", "destinationCountry", "documentGroup", "documentType",
            "documentName", "tripType", "required", "typeName", "insuredValue", "stopOrder");

    private final SystemLogRepository repository;
    private final UserRepository users;
    private final StaffRepository staff;
    private final ObjectMapper mapper;

    public record Actor(User user, String name, String email, String role) {}

    /** Capture before editing an account, including the actor's own username/email. */
    public Actor actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Không xác định được người thực hiện thao tác.");
        }
        User user = users.findByUsernameOrEmail(authentication.getName(), authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("Không tìm thấy tài khoản thực hiện thao tác."));
        return new Actor(user, user.getUsername(), user.getEmail(),
                staff.findById(user.getId()).map(s -> s.getRole()).orElse("CUSTOMER"));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String action, String target, Object key, String label, String description,
            Map<String, ?> before, Map<String, ?> after) {
        if (!TARGETS.contains(target)) throw new IllegalArgumentException("Loại đối tượng nhật ký không hợp lệ.");
        if (!ACTIONS.get(target).contains(action)) throw new IllegalArgumentException("Hành động nhật ký không hợp lệ.");
        if (before != null && after != null && Objects.equals(before, after)) return;
        recordAs(actor(), action, target, key, label, description, before, after);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordAs(Actor actor, String action, String target, Object key, String label, String description,
            Map<String, ?> before, Map<String, ?> after) {
        if (!TARGETS.contains(target) || !ACTIONS.get(target).contains(action)) throw new IllegalArgumentException("Hành động nhật ký không hợp lệ.");
        if (before != null && after != null && Objects.equals(before, after)) return;
        if (actor == null) throw new AccessDeniedException("Không xác định được người thực hiện thao tác.");
        String targetKey = String.valueOf(key);
        Integer numericKey = key instanceof Integer i ? i : null;
        repository.save(SystemLog.builder()
                .actionbyUserid(actor.user()).actorName(actor.name()).actorEmail(actor.email()).actorRole(actor.role())
                .actionType(action).targetTable(target).targetRecordID(numericKey)
                .targetKey(targetKey).targetLabel(label).description(description)
                .oldData(json(before)).newData(json(after)).dataVersion(1).createdAt(Instant.now()).build());
    }

    private String json(Map<String, ?> values) {
        if (values == null) return null;
        validateSnapshot(values);
        try {
            return mapper.writeValueAsString(values);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Không thể lưu nhật ký thay đổi.", ex);
        }
    }

    private void validateSnapshot(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) return;
        if (value instanceof Map<?, ?> fields) {
            if (!FIELDS.containsAll(fields.keySet())) throw new IllegalArgumentException("Trường dữ liệu nhật ký không được phép.");
            fields.values().forEach(this::validateSnapshot);
        } else if (value instanceof Iterable<?> list) {
            list.forEach(this::validateSnapshot);
        } else {
            throw new IllegalArgumentException("Dữ liệu nhật ký phải là giá trị được phép.");
        }
    }

    public static Map<String, Object> values(Object... pairs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) values.put((String) pairs[i], pairs[i + 1]);
        return values;
    }
}
