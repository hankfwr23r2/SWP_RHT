package com.example.racehorse_transport.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** The declaration and policy for one horse on one booking, separate from issued quote snapshots. */
@Getter
@Setter
@Entity
@Table(name = "booking_horse_insurance", uniqueConstraints =
        @UniqueConstraint(name = "uk_booking_horse_insurance_pair", columnNames = {"bookingid", "horseid"}))
public class BookingHorseInsurance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "insurance_id", nullable = false)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
            @JoinColumn(name = "bookingid", referencedColumnName = "BookingID", nullable = false),
            @JoinColumn(name = "horseid", referencedColumnName = "HorseID", nullable = false)
    })
    private BookingHorse bookingHorse;

    @Column(name = "insurance_option", length = 20)
    private String option;

    @Column(name = "provider_name")
    private String provider;

    @Column(name = "policy_number")
    private String policyNumber;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "quote_valid_until")
    private LocalDate quoteValidUntil;

    @Column(name = "premium_amount", precision = 18, scale = 2)
    private BigDecimal premium;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "reviewed")
    private Boolean reviewed;

    @Column(name = "arranged")
    private Boolean arranged;

    // Preserve the API distinction between an omitted collection and an explicitly empty one.
    @Column(name = "documents_present", nullable = false)
    private boolean documentsPresent;

    @Column(name = "policy_documents_present", nullable = false)
    private boolean policyDocumentsPresent;

    @Column(name = "document_names_present", nullable = false)
    private boolean documentNamesPresent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_userid")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "arranged_by_userid")
    private User arrangedBy;

    @Column(name = "arranged_at")
    private Instant arrangedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToMany(mappedBy = "insurance", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<InsuranceDocument> insuranceDocuments = new ArrayList<>();

    @PrePersist
    void initializeTimestamps() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }
}
