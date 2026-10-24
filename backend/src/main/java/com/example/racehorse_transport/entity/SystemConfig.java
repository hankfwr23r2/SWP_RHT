package com.example.racehorse_transport.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

/**
 * Bảng cấu hình tham số nghiệp vụ hệ thống (System Config / Business Rules).
 * Mỗi bản ghi là một cặp key-value cho phép Admin điều chỉnh linh hoạt mà không cần deploy lại code.
 * Ví dụ: MAX_CONTINUOUS_HOURS=4, DEPOSIT_RATE=0.3, QUOTE_VALID_HOURS=24, DEMURRAGE_PER_HOUR=400000.
 */
@Getter
@Setter
@Entity
@Table(name = "SYSTEM_CONFIG")
public class SystemConfig {

    @Id
    @Column(name = "config_key", nullable = false, length = 100)
    private String configKey;

    @NotBlank
    @Size(max = 255)
    @Nationalized
    @Column(name = "config_name", nullable = false, length = 255)
    private String configName;

    @NotBlank
    @Column(name = "config_value", nullable = false, length = 500)
    private String configValue;

    @Size(max = 50)
    @Column(name = "unit", length = 50)
    private String unit;

    @NotBlank
    @Size(max = 50)
    @Column(name = "config_group", nullable = false, length = 50)
    private String configGroup;

    @Column(name = "description", columnDefinition = "TEXT")
    @Nationalized
    private String description;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
