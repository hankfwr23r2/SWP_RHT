package com.example.racehorse_transport.entity;

import com.example.racehorse_transport.entity.enums.RuleDirection;

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

import java.time.LocalDate;

// Quy định theo nước và hướng đi. Cùng nước, cùng hướng không trùng hiệu lực (EXCLUDE ở DB).
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "country_rule")
public class CountryRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // Nước đối tác ngoài VN. Với INTERPROVINCE dùng VN
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "country_code", nullable = false)
    private Country country;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", length = 30, nullable = false)
    private RuleDirection direction;

    @Column(name = "permit_lead_days", nullable = false)
    private Integer permitLeadDays;

    @Column(name = "certificate_lead_days", nullable = false)
    private Integer certificateLeadDays;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;
}
