package com.example.racehorse_transport.dto.manager;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendQuoteRequest {
    private String by;
    private List<QuoteAdjustmentDto> adjustments;
    private List<QuoteLineDto> lines;
    private BigDecimal subtotal;
    private BigDecimal total;
    private BigDecimal deposit;
    private BigDecimal balance;
}
