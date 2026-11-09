package com.example.racehorse_transport.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @JsonIgnore
    @Version
    @Builder.Default
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "BookingID")
    private Booking bookingID;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "StaffID")
    private Staff staffID;

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

    @JsonIgnore
    @Column(name = "expenses_normalized", nullable = false)
    private boolean expensesNormalized;

    @JsonIgnore
    @Builder.Default
    @OneToMany(mappedBy = "incident", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("expensePosition ASC")
    private List<IncidentExpense> expenseItems = new ArrayList<>();

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

    public String getExpensesJson() {
        if (!expensesNormalized || expensesJson == null) return expensesJson;
        ArrayNode array = FinancialJsonSupport.JSON.createArrayNode();
        expenseItems.stream().sorted(Comparator.comparingInt(IncidentExpense::getExpensePosition))
                .forEach(expense -> array.add(expense.toJson()));
        try {
            return FinancialJsonSupport.JSON.writeValueAsString(array);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot reconstruct normalized incident expenses", ex);
        }
    }

    public void setExpensesJson(String json) {
        if (json == null) {
            expenseItems.clear();
            expensesJson = null;
            expensesNormalized = true;
            return;
        }
        try {
            JsonNode root = FinancialJsonSupport.JSON.readTree(json);
            if (!(root instanceof ArrayNode array)) {
                throw new IllegalArgumentException("Expected an incident expenses array");
            }
            List<IncidentExpense> parsed = new ArrayList<>();
            for (int position = 0; position < array.size(); position++) {
                parsed.add(IncidentExpense.fromJson(array.get(position), position));
            }
            reconcileExpenses(parsed);
            expensesJson = "[]";
            expensesNormalized = true;
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            expenseItems.clear();
            expensesJson = json;
            expensesNormalized = false;
        }
    }

    @JsonIgnore
    public String getStoredExpensesJson() {
        return expensesJson;
    }

    @PrePersist
    void normalizeBuilderExpenses() {
        if (!expensesNormalized) setExpensesJson(expensesJson);
    }

    private void reconcileExpenses(List<IncidentExpense> parsed) {
        Map<String, Deque<IncidentExpense>> existing = new HashMap<>();
        for (IncidentExpense expense : expenseItems) {
            existing.computeIfAbsent(expenseKey(expense), ignored -> new ArrayDeque<>()).add(expense);
        }
        List<IncidentExpense> retained = new ArrayList<>();
        for (IncidentExpense source : parsed) {
            Deque<IncidentExpense> matches = existing.get(expenseKey(source));
            IncidentExpense target = matches == null || matches.isEmpty() ? null : matches.removeFirst();
            if (target == null) {
                target = source;
                target.setIncident(this);
                expenseItems.add(target);
            } else {
                target.copyValues(source);
            }
            retained.add(target);
        }
        expenseItems.removeIf(expense -> !retained.contains(expense));
    }

    private static String expenseKey(IncidentExpense expense) {
        return expense.getExpenseId() == null
                ? "position:" + expense.getExpensePosition() : "id:" + expense.getExpenseId();
    }
}
