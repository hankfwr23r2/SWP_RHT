package com.example.racehorse_transport.dto.incident;

import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentResponse {

    private Integer id;
    private String formattedId; // INC-001
    private Integer bookingId;
    private String bookingCode; // BK-003
    private String reporter;
    private String incidentName;
    private String incidentType;
    private String description;
    private String status; // open, proposed, active, resolved, rejected
    private String resolutionType; // REROUTE, EXTERNAL_PARTNER, FORCE_MAJEURE, HANDOVER_TIMEOUT
    private String partnerName;
    private String partnerService;
    private String partnerPhone;
    private BigDecimal estimatedCost;
    private BigDecimal actualCost;
    private String costBearer; // COMPANY, CUSTOMER
    private String invoiceUrl;
    private String directive;
    private Long createdAt;
    private Long resolvedAt;
}
