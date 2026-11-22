package com.example.racehorse_transport.entity;

import com.example.racehorse_transport.entity.enums.HorseGender;
import com.example.racehorse_transport.entity.enums.HorseStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

// Hồ sơ ngựa. Ngựa đang ở đâu tính từ handover mới nhất, không lưu ở đây.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "horse")
public class Horse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_party_id", nullable = false)
    private Party owner;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "microchip", length = 30)
    private String microchip;

    @Column(name = "passport_no", length = 50)
    private String passportNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 30, nullable = false)
    private HorseGender gender;

    @Column(name = "birth_year")
    private Short birthYear;

    @Column(name = "color", length = 50)
    private String color;

    @Column(name = "breed", length = 100)
    private String breed;

    @Column(name = "sire", length = 100)
    private String sire;

    @Column(name = "dam", length = 100)
    private String dam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "horse_type_id", nullable = false)
    private HorseType horseType;

    @Column(name = "identification_marks", columnDefinition = "text")
    private String identificationMarks;

    @Column(name = "weight_kg", precision = 6, scale = 1)
    private BigDecimal weightKg;

    @Column(name = "declared_value", precision = 15, scale = 2)
    private BigDecimal declaredValue;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "declared_currency", length = 3)
    private String declaredCurrency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private HorseStatus status;

    // DB đặt giá trị mặc định now() lúc thêm dòng
    @Generated
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
