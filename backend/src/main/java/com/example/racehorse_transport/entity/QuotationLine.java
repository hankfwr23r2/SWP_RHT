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
@Table(name = "quotation_line", uniqueConstraints = @UniqueConstraint(
        name = "uq_quotation_line_position", columnNames = {"quote_id", "line_type", "line_position"}))
public class QuotationLine {
    private static final Set<String> LINE_FIELDS = Set.of("label", "detail", "calculation", "amount");
    private static final Set<String> ADJUSTMENT_FIELDS = Set.of("label", "amount");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quote_id", nullable = false)
    private Quotation quotation;

    @Column(name = "line_type", nullable = false, length = 16)
    private String lineType;

    @Column(name = "line_position", nullable = false)
    private int linePosition;

    @Column(columnDefinition = "TEXT")
    private String label;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(columnDefinition = "TEXT")
    private String calculation;

    @Column(columnDefinition = "NUMERIC")
    private BigDecimal amount;

    @Column(name = "present_fields", nullable = false)
    private int presentFields;

    static QuotationLine fromJson(JsonNode node, String type, int position) {
        FinancialJsonSupport.requireFields(node, "LINE".equals(type) ? LINE_FIELDS : ADJUSTMENT_FIELDS);
        QuotationLine line = new QuotationLine();
        line.lineType = type;
        line.linePosition = position;
        line.label = FinancialJsonSupport.text(node, "label");
        line.detail = FinancialJsonSupport.text(node, "detail");
        line.calculation = FinancialJsonSupport.text(node, "calculation");
        line.amount = FinancialJsonSupport.amount(node, "amount");
        line.presentFields = FinancialJsonSupport.presentFields(node, "label", "detail", "calculation", "amount");
        return line;
    }

    void copyValues(QuotationLine source) {
        lineType = source.lineType;
        linePosition = source.linePosition;
        label = source.label;
        detail = source.detail;
        calculation = source.calculation;
        amount = source.amount;
        presentFields = source.presentFields;
    }

    ObjectNode toJson() {
        ObjectNode node = FinancialJsonSupport.JSON.createObjectNode();
        FinancialJsonSupport.put(node, presentFields, 0, "label", label);
        FinancialJsonSupport.put(node, presentFields, 1, "detail", detail);
        FinancialJsonSupport.put(node, presentFields, 2, "calculation", calculation);
        FinancialJsonSupport.put(node, presentFields, 3, "amount", amount);
        return node;
    }
}
