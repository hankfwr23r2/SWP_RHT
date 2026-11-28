package com.example.racehorse_transport.entity;

import com.example.racehorse_transport.entity.enums.IncidentPlanStatus;
import com.example.racehorse_transport.entity.enums.IncidentResolutionType;

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

import java.math.BigDecimal;
import java.time.Instant;

// Phương án xử lý sự cố, có phiên bản. Mỗi sự cố chỉ có một phương án APPROVED (UK một phần ở DB).
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "incident_plan")
public class IncidentPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_type", length = 30, nullable = false)
    private IncidentResolutionType resolutionType;

    @Column(name = "instructions", columnDefinition = "text", nullable = false)
    private String instructions;

    @Column(name = "estimated_cost", precision = 15, scale = 2)
    private BigDecimal estimatedCost;

    // Bên xử lý: thú y, cứu hộ, chuồng gửi
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "handler_party_id")
    private Party handlerParty;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private IncidentPlanStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposed_by", nullable = false)
    private Staff proposedBy;

    // DB đặt giá trị mặc định now() lúc thêm dòng
    @Generated
    @Column(name = "proposed_at", nullable = false, insertable = false, updatable = false)
    private Instant proposedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "decided_by")
    private Staff decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_agreed_by")
    private AppUser customerAgreedBy;

    @Column(name = "customer_agreed_at")
    private Instant customerAgreedAt;
}
