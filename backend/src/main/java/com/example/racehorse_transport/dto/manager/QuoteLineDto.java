package com.example.racehorse_transport.dto.manager;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteLineDto {
    private String label;
    private String detail;
    private BigDecimal amount;
}
