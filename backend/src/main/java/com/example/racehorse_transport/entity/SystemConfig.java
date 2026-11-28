package com.example.racehorse_transport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.Generated;

import java.time.Instant;

// Tham số kỹ thuật (không phải giá tiền), ví dụ thời gian chờ giao, hạn OTP. Giá cước nằm ở price_list/price_item.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "system_config")
public class SystemConfig {

    @Id
    @Column(name = "config_key", length = 100, nullable = false)
    private String configKey;

    @Generated
    @Column(name = "audit_target_id", nullable = false, insertable = false, updatable = false)
    private Long auditTargetId;

    @Column(name = "config_value", length = 500, nullable = false)
    private String configValue;

    @Column(name = "min_value", insertable = false, updatable = false)
    private Integer minValue;

    @Column(name = "max_value", insertable = false, updatable = false)
    private Integer maxValue;

    // MINUTE, HOUR, DAY, COUNT
    @Column(name = "unit", length = 30)
    private String unit;

    @Column(name = "config_group", length = 50, nullable = false)
    private String configGroup;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private Staff updatedBy;

    // DB đặt mặc định lúc thêm dòng. Service cập nhật khi thay tham số.
    @Generated
    @Column(name = "updated_at", nullable = false, insertable = false)
    private Instant updatedAt;
}
