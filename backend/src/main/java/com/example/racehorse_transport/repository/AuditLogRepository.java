package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.AuditLog;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    long countByTargetTableAndTargetIdAndAction(String targetTable, Long targetId, String action);

    boolean existsByTargetTableAndTargetIdAndActionAndCreatedAtGreaterThanEqual(
            String targetTable, Long targetId, String action, Instant from);
    long countByTargetTableAndTargetIdAndActionStartingWithAndCreatedAtGreaterThanEqual(
            String targetTable, Long targetId, String action, Instant from);

    // Các cặp bảng, hành động đã có trong nhật ký (bộ lọc của trang nhật ký hệ thống)
    @Query("SELECT DISTINCT a.targetTable, a.action FROM AuditLog a ORDER BY a.targetTable, a.action")
    List<Object[]> findTargetActions();

    // Lọc nhật ký: [from, to), các điều kiện NULL thì bỏ qua; actor khớp một phần tên đăng nhập hoặc email
    static Specification<AuditLog> matching(Instant from, Instant to, String target, String action, String role, Long targetId, String actor) {
        return (root, query, cb) -> {
            var user = root.join("actorUser", JoinType.LEFT);
            List<Predicate> where = new ArrayList<>();
            where.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            where.add(cb.lessThan(root.get("createdAt"), to));
            if (target != null) where.add(cb.equal(root.get("targetTable"), target));
            if (action != null) where.add(cb.equal(root.get("action"), action));
            if (targetId != null) where.add(cb.equal(root.get("targetId"), targetId));
            if (role != null) where.add(cb.equal(user.join("role", JoinType.LEFT).get("code"), role));
            if (actor != null) {
                String like = "%" + actor.toLowerCase(Locale.ROOT) + "%";
                where.add(cb.or(cb.like(cb.lower(user.get("username")), like), cb.like(cb.lower(user.get("email")), like)));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }
}
