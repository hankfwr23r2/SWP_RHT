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
    private Boolean reviewed;
    private Boolean arranged;
}
