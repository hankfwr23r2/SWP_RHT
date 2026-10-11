package com.example.racehorse_transport.dto.manager;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteAdjustmentDto {
    private String label;
    private BigDecimal amount;
}
