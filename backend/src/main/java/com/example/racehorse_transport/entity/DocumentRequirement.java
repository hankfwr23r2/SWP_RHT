package com.example.racehorse_transport.entity;

import com.example.racehorse_transport.entity.enums.DocumentSubmitter;

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

// Theo quy định thì cần giấy gì, ai nộp.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "document_requirement")
public class DocumentRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_id", nullable = false)
    private CountryRule rule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_type_id", nullable = false)
    private DocumentType documentType;

    // Chỉ áp cho một cửa khẩu. NULL = mọi cửa khẩu
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "gate_location_id")
    private Location gateLocation;

    @Enumerated(EnumType.STRING)
    @Column(name = "submitted_by", length = 30, nullable = false)
    private DocumentSubmitter submittedBy;

    @Column(name = "is_mandatory", nullable = false)
    private Boolean mandatory = true;
}
