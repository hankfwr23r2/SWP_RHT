package com.example.racehorse_transport.dto.booking;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class HorseInsuranceDto {
    private String option; // personal, assistance, none
    private String provider;
    private String policyNumber;
    private String validFrom;
    private String validUntil;
    private BigDecimal premium;
    private String note;
    private List<String> documents;
    private java.util.Map<String, String> documentNames;
    private List<String> policyDocuments;
    private String quoteValidUntil;
    private Boolean reviewed;
    private Boolean arranged;
    // Chuyên viên yêu cầu khách bổ sung bảo hiểm qua luồng Bổ sung hồ sơ; null khi không chờ khách.
    private String revisionReason;
    private Long revisionRequestedAt;
    // Lần bổ sung gần nhất của khách, để chuyên viên biết khách đã bổ sung hay đổi loại.
    private InsuranceRevisionDto lastRevision;
}
