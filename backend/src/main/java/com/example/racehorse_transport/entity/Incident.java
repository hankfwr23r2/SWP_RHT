package com.example.racehorse_transport.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "INCIDENT")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "IncidentID", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "BookingID")
    private Booking bookingID;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "StaffID")
    private Staff staffID;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "LogID")
    private Log logID;

    @Size(max = 100)
    @Column(name = "trip_id", length = 100)
    private String tripId;

    @Size(max = 500)
    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(name = "location_lat")
    private Double locationLat;

    @Column(name = "location_lng")
    private Double locationLng;

    @Column(name = "plan_json", columnDefinition = "TEXT")
    private String planJson;

    @Column(name = "expenses_json", columnDefinition = "TEXT")
    private String expensesJson;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "rejection_at")
    private Instant rejectionAt;

    @Size(max = 100)
    @Column(name = "rejection_by", length = 100)
    private String rejectionBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Size(max = 100)
    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "fit_confirmed_at")
    private Instant fitConfirmedAt;

    @Size(max = 100)
    @Column(name = "fit_confirmed_by", length = 100)
    private String fitConfirmedBy;

    @Size(max = 255)
    @Nationalized
    @Column(name = "incident_name")
    private String incidentName;

    @Size(max = 100)
    @Column(name = "incident_type", length = 100)
    private String incidentType; // VEHICLE_BREAKDOWN, HORSE_HEALTH, TRAFFIC_DELAY, HANDOVER_TIMEOUT

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Size(max = 50)
    @Column(name = "status", length = 50)
    private String status; // open, proposed, active, resolved, rejected

    @Size(max = 50)
    @Column(name = "resolution_type", length = 50)
    private String resolutionType; // REROUTE, EXTERNAL_PARTNER, FORCE_MAJEURE, HANDOVER_TIMEOUT

    @Size(max = 255)
    @Nationalized
    @Column(name = "partner_name")
    private String partnerName;

    @Size(max = 255)
    @Nationalized
    @Column(name = "partner_service")
    private String partnerService;

    @Size(max = 50)
    @Column(name = "partner_phone", length = 50)
    private String partnerPhone;

    @Column(name = "estimated_cost", precision = 18, scale = 2)
    private BigDecimal estimatedCost;

    @Column(name = "actual_cost", precision = 18, scale = 2)
    private BigDecimal actualCost;

    @Size(max = 50)
    @Column(name = "cost_bearer", length = 50)
    private String costBearer; // COMPANY, CUSTOMER

    @Size(max = 500)
    @Column(name = "invoice_url", length = 500)
    private String invoiceUrl;

    @Column(name = "directive", columnDefinition = "TEXT")
    private String directive;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
