package com.example.racehorse_transport.dto.settlement;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementResponse {
    private Integer bookingId;
    private String bookingCode;
    private String status;
    private List<ExpenseItemDto> items;
    private BigDecimal totalCompanyCost;
    private Long reviewedAt;
    private String reviewedBy;
}
