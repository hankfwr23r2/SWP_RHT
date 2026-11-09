package com.example.racehorse_transport.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "incident_expense", indexes = @Index(
        name = "idx_incident_expense_position", columnList = "incident_id,expense_position"))
public class IncidentExpense {
    private static final Set<String> FIELDS = Set.of("id", "category", "label", "photo", "amount", "payer", "at", "by");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Column(name = "expense_id", columnDefinition = "TEXT")
    private String expenseId;

    @Column(name = "expense_position", nullable = false)
    private int expensePosition;

    @Column(columnDefinition = "TEXT")
    private String category;

    @Column(columnDefinition = "TEXT")
    private String label;

    @Column(columnDefinition = "TEXT")
    private String photo;

    @Column(columnDefinition = "NUMERIC")
    private BigDecimal amount;

    @Column(columnDefinition = "TEXT")
    private String payer;

    @Column(name = "occurred_at_epoch_ms")
    private Long occurredAtEpochMs;

    @Column(name = "recorded_by", columnDefinition = "TEXT")
    private String recordedBy;

    @Column(name = "present_fields", nullable = false)
    private int presentFields;

    static IncidentExpense fromJson(JsonNode node, int position) {
        FinancialJsonSupport.requireFields(node, FIELDS);
        IncidentExpense expense = new IncidentExpense();
        expense.expensePosition = position;
        expense.expenseId = FinancialJsonSupport.text(node, "id");
        expense.category = FinancialJsonSupport.text(node, "category");
        expense.label = FinancialJsonSupport.text(node, "label");
        expense.photo = FinancialJsonSupport.text(node, "photo");
        expense.amount = FinancialJsonSupport.amount(node, "amount");
        expense.payer = FinancialJsonSupport.text(node, "payer");
        expense.occurredAtEpochMs = FinancialJsonSupport.integer(node, "at");
        expense.recordedBy = FinancialJsonSupport.text(node, "by");
        expense.presentFields = FinancialJsonSupport.presentFields(node, "id", "category", "label", "photo", "amount", "payer", "at", "by");
        return expense;
    }

    void copyValues(IncidentExpense source) {
        expenseId = source.expenseId;
        expensePosition = source.expensePosition;
        category = source.category;
        label = source.label;
        photo = source.photo;
        amount = source.amount;
        payer = source.payer;
        occurredAtEpochMs = source.occurredAtEpochMs;
        recordedBy = source.recordedBy;
        presentFields = source.presentFields;
    }

    ObjectNode toJson() {
        ObjectNode node = FinancialJsonSupport.JSON.createObjectNode();
        FinancialJsonSupport.put(node, presentFields, 0, "id", expenseId);
        FinancialJsonSupport.put(node, presentFields, 1, "category", category);
        FinancialJsonSupport.put(node, presentFields, 2, "label", label);
        FinancialJsonSupport.put(node, presentFields, 3, "photo", photo);
        FinancialJsonSupport.put(node, presentFields, 4, "amount", amount);
        FinancialJsonSupport.put(node, presentFields, 5, "payer", payer);
        FinancialJsonSupport.put(node, presentFields, 6, "at", occurredAtEpochMs);
        FinancialJsonSupport.put(node, presentFields, 7, "by", recordedBy);
        return node;
    }
}
