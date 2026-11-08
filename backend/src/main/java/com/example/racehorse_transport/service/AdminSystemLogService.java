package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.admin.SystemLogResponse;
import com.example.racehorse_transport.entity.SystemLog;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.SystemLogRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSystemLogService {
    private final SystemLogRepository repository;

    public record LogPage(List<SystemLogResponse> content, long totalElements, int totalPages, int number, int size) {}

    public LogPage search(String target, String action, String actor, String key, Instant from, Instant to, int page, int size) {
        return search(target, action, actor, key, from, to, page, size, null);
    }

    public LogPage search(String target, String action, String actor, String key, Instant from, Instant to, int page, int size, String role) {
        if (page < 0 || page > 100000 || size < 1 || size > 100) throw new IllegalArgumentException("Phân trang không hợp lệ.");
        if (target != null && !target.isBlank() && !AdminAuditWriter.TARGETS.contains(target)) {
            throw new IllegalArgumentException("Nhóm nhật ký không hợp lệ.");
        }
        if (from != null && to != null && !from.isBefore(to)) throw new IllegalArgumentException("Ngày bắt đầu phải trước ngày kết thúc.");
        if (has(role) && !java.util.Set.of("ADMIN", "MANAGER", "COORDINATOR", "SPECIALIST", "DRIVER", "ESCORT", "CUSTOMER").contains(role)) {
            throw new IllegalArgumentException("Vai trò không hợp lệ.");
        }
        for (String text : new String[]{action, actor, key}) {
            if (text != null && text.length() > 255) throw new IllegalArgumentException("Nội dung tìm kiếm quá dài.");
        }
        Specification<SystemLog> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("targetTable").in(AdminAuditWriter.TARGETS));
            if (has(target)) predicates.add(cb.equal(root.get("targetTable"), target));
            if (has(action)) predicates.add(cb.equal(root.get("actionType"), action.trim()));
            if (has(role)) predicates.add(cb.equal(root.get("actorRole"), role));
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null) predicates.add(cb.lessThan(root.get("createdAt"), to));
            if (has(key)) {
                if (key.trim().matches("[0-9]+")) {
                    Predicate exactKey = cb.equal(root.get("targetKey"), key.trim());
                    try {
                        exactKey = cb.or(exactKey, cb.equal(root.get("targetRecordID"), Integer.valueOf(key.trim())));
                    } catch (NumberFormatException ignored) {
                        // Oversized numeric keys cannot refer to a legacy integer ID.
                    }
                    predicates.add(exactKey);
                } else {
                    predicates.add(cb.or(cb.like(cb.lower(root.get("targetKey")), like(key), '\\'),
                            cb.like(cb.lower(root.get("targetLabel")), like(key), '\\')));
                }
            }
            if (has(actor)) {
                var user = root.join("actionbyUserid", JoinType.LEFT);
                predicates.add(cb.or(cb.like(cb.lower(root.get("actorName")), like(actor), '\\'),
                        cb.like(cb.lower(root.get("actorEmail")), like(actor), '\\'),
                        cb.and(cb.isNull(root.get("dataVersion")), cb.or(
                                cb.like(cb.lower(user.get("username")), like(actor), '\\'),
                                cb.like(cb.lower(user.get("email")), like(actor), '\\')))));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        var result = repository.findAll(specification, PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return new LogPage(result.getContent().stream().map(this::toResponse).toList(), result.getTotalElements(), result.getTotalPages(), page, size);
    }

    public SystemLogResponse detail(Integer id) {
        SystemLog entry = repository.findById(id)
                .filter(log -> log.getTargetTable() != null && AdminAuditWriter.TARGETS.contains(log.getTargetTable()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhật ký quản trị."));
        return toResponse(entry);
    }

    private SystemLogResponse toResponse(SystemLog log) {
        boolean legacy = log.getDataVersion() == null;
        var user = log.getActionbyUserid();
        return new SystemLogResponse(log.getId(), log.getActionType(), user == null ? null : user.getId(),
                legacy && user != null ? user.getUsername() : log.getActorName(),
                legacy && user != null ? user.getEmail() : log.getActorEmail(), log.getActorRole(), log.getTargetTable(),
                log.getTargetKey() != null ? log.getTargetKey() : log.getTargetRecordID() == null ? null : String.valueOf(log.getTargetRecordID()),
                log.getTargetLabel(), log.getDescription(), log.getOldData(), log.getNewData(), legacy, log.getCreatedAt());
    }

    private static boolean has(String value) { return value != null && !value.isBlank(); }
    private static String like(String value) {
        if (value.length() > 255) throw new IllegalArgumentException("Nội dung tìm kiếm quá dài.");
        return "%" + value.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
