package com.example.racehorse_transport.entity;

import com.example.racehorse_transport.entity.enums.PriceListStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;

// Bảng giá có thời hạn hiệu lực. Các bảng ACTIVE cùng tiền tệ không trùng hiệu lực (EXCLUDE ở DB).
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "price_list")
public class PriceList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_code", length = 3, nullable = false)
    private String currencyCode;

    @Column(name = "margin_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal marginRate;

    @Column(name = "fuel_buffer_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal fuelBufferRate;

    @Column(name = "deposit_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal depositRate;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private PriceListStatus status;
}
