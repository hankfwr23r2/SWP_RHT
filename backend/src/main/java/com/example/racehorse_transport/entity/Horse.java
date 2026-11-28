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
    @JoinColumn(name = "owner_customer_id", nullable = false)
    private Customer owner;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "microchip", length = 30)
    private String microchip;

    @Enumerated(EnumType.STRING)
    @Column(name = "sex", length = 30, nullable = false)
    private HorseGender sex;

    @Column(name = "birth_year")
    private Short birthYear;

    @Column(name = "color", length = 50)
    private String color;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "horse_type_id", nullable = false)
    private HorseType horseType;

    @Column(name = "identification_marks", columnDefinition = "text")
    private String identificationMarks;

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
