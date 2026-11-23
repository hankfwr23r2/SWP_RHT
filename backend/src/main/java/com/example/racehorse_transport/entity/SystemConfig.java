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
    @Column(name = "key", length = 100, nullable = false)
    private String key;

    @Column(name = "value", length = 500, nullable = false)
    private String value;

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

    // DB đặt giá trị mặc định now() lúc thêm dòng
    @Generated
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
