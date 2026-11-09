package com.example.racehorse_transport.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "QUOTATION")
public class Quotation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "quote_id", nullable = false)
    private Integer id;

    @JsonIgnore
    @Version
    @Builder.Default
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bookingid", nullable = false)
    private Booking booking;

    @Column(name = "subtotal", precision = 18, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "adjustment_amount", precision = 18, scale = 2)
    private BigDecimal adjustmentAmount;

    @Size(max = 255)
    @Column(name = "adjustment_note")
    private String adjustmentNote;

    @Column(name = "total_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "deposit_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal depositAmount; // 30%

    @Column(name = "balance_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal balanceAmount; // 70%

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sent_by_userid")
    private User sentByUser;

    @Size(max = 50)
    @Column(name = "status", length = 50)
    private String status; // PENDING, ACCEPTED, REJECTED, EXPIRED

    @Column(name = "quote_lines_json", columnDefinition = "TEXT")
    private String quoteLinesJson;

    @JsonIgnore
    @Column(name = "lines_normalized", nullable = false)
    private boolean linesNormalized;

    @JsonIgnore
    @Builder.Default
    @OneToMany(mappedBy = "quotation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineType ASC, linePosition ASC")
    private List<QuotationLine> lineItems = new ArrayList<>();

    /** Existing service/API callers still receive the original quotation JSON shape. */
    public String getQuoteLinesJson() {
        if (!linesNormalized || quoteLinesJson == null) return quoteLinesJson;
        try {
            JsonNode root = FinancialJsonSupport.JSON.readTree(quoteLinesJson);
            if (root.isArray()) return FinancialJsonSupport.JSON.writeValueAsString(lineArray("LINE"));
            if (!(root instanceof ObjectNode object)) {
                throw new IllegalStateException("Invalid normalized quotation envelope");
            }
            if (object.path("lines").isArray()) object.set("lines", lineArray("LINE"));
            if (object.path("adjustments").isArray()) object.set("adjustments", lineArray("ADJUSTMENT"));
            return FinancialJsonSupport.JSON.writeValueAsString(object);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot reconstruct normalized quotation", ex);
        }
    }

    public void setQuoteLinesJson(String json) {
        if (json == null) {
            lineItems.clear();
            quoteLinesJson = null;
            linesNormalized = true;
            return;
        }
        try {
            JsonNode root = FinancialJsonSupport.JSON.readTree(json);
            List<QuotationLine> parsed = new ArrayList<>();
            String residual;
            if (root instanceof ArrayNode array) {
                parseLines(array, "LINE", parsed);
                residual = "[]";
            } else if (root instanceof ObjectNode object) {
                for (String field : List.of("lines", "adjustments")) {
                    JsonNode value = object.get(field);
                    if (value == null || value.isNull()) continue;
                    if (!(value instanceof ArrayNode array)) {
                        throw new IllegalArgumentException("Expected a quotation array: " + field);
                    }
                    parseLines(array, "lines".equals(field) ? "LINE" : "ADJUSTMENT", parsed);
                    object.set(field, FinancialJsonSupport.JSON.createArrayNode());
                }
                residual = FinancialJsonSupport.JSON.writeValueAsString(object);
            } else {
                throw new IllegalArgumentException("Unsupported legacy quotation shape");
            }
            reconcileLines(parsed);
            quoteLinesJson = residual;
            linesNormalized = true;
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            // Unknown fields/types retain the full payload for an explicit later migration.
            lineItems.clear();
            quoteLinesJson = json;
            linesNormalized = false;
        }
    }

    @JsonIgnore
    public String getStoredQuoteLinesJson() {
        return quoteLinesJson;
    }

    @PrePersist
    void normalizeBuilderLines() {
        if (!linesNormalized) setQuoteLinesJson(quoteLinesJson);
    }

    private static void parseLines(ArrayNode array, String type, List<QuotationLine> parsed) {
        for (int position = 0; position < array.size(); position++) {
            parsed.add(QuotationLine.fromJson(array.get(position), type, position));
        }
    }

    private ArrayNode lineArray(String type) {
        ArrayNode array = FinancialJsonSupport.JSON.createArrayNode();
        lineItems.stream().filter(line -> type.equals(line.getLineType()))
                .sorted(Comparator.comparingInt(QuotationLine::getLinePosition))
                .forEach(line -> array.add(line.toJson()));
        return array;
    }

    private void reconcileLines(List<QuotationLine> parsed) {
        Map<String, QuotationLine> existing = new HashMap<>();
        for (QuotationLine line : lineItems) {
            existing.put(line.getLineType() + ":" + line.getLinePosition(), line);
        }
        List<QuotationLine> retained = new ArrayList<>();
        for (QuotationLine source : parsed) {
            QuotationLine target = existing.get(source.getLineType() + ":" + source.getLinePosition());
            if (target == null) {
                target = source;
                target.setQuotation(this);
                lineItems.add(target);
            } else {
                target.copyValues(source);
            }
            retained.add(target);
        }
        lineItems.removeIf(line -> !retained.contains(line));
    }
}
