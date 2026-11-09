package com.example.racehorse_transport.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A document reference only; removing a row never deletes a file from cloud storage. */
@Getter
@Setter
@Entity
@Table(name = "insurance_document")
public class InsuranceDocument {
    public static final String DECLARATION = "DECLARATION";
    public static final String POLICY = "POLICY";
    public static final String NAME_ONLY = "NAME_ONLY";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "document_id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "insurance_id", nullable = false)
    private BookingHorseInsurance insurance;

    @Column(name = "document_kind", nullable = false, length = 20)
    private String kind;

    @Column(name = "url", columnDefinition = "TEXT", nullable = false)
    private String url;

    @Column(name = "original_name", columnDefinition = "TEXT")
    private String originalName;

    @Column(name = "named", nullable = false)
    private boolean named;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
