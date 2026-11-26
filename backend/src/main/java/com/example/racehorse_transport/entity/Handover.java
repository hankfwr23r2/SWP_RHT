package com.example.racehorse_transport.entity;

import com.example.racehorse_transport.entity.enums.HandoverStatus;
import com.example.racehorse_transport.entity.enums.HandoverType;

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

import java.time.Instant;

// Một lần chuyển giao ngựa giữa các bên. Bên giao và bên nhận suy ra từ handoverType; biên bản ký là document trỏ về dòng này.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "handover")
public class Handover {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "handover_type", length = 30, nullable = false)
    private HandoverType handoverType;

    @Enumerated(EnumType.STRING)
    @Column(name = "unclaimed_outcome", length = 40)
    private com.example.racehorse_transport.entity.enums.UnclaimedHorseOutcome unclaimedOutcome;

    // Giao nhận tại điểm dừng của xe hoặc khi nhập/xuất nơi lưu trú, đúng một (CHECK ở DB)
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "trip_stop_id")
    private TripStop tripStop;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "horse_stay_id")
    private HorseStay horseStay;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_plan_id")
    private IncidentPlan incidentPlan;

    // NULL = công ty hoặc bên trong đơn
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "from_party_id")
    private Party fromParty;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "to_party_id")
    private Party toParty;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "handled_by", nullable = false)
    private Staff handledBy;

    @Column(name = "wait_started_at")
    private Instant waitStartedAt;

    @Column(name = "handed_at")
    private Instant handedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private HandoverStatus status;

    @Column(name = "note", columnDefinition = "text")
    private String note;
}
