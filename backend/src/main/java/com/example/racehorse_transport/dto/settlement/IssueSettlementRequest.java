package com.example.racehorse_transport.dto.settlement;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueSettlementRequest {
    private String managerName;
    private List<ExpenseItemDto> items;
    private BigDecimal adjustment; // Chiết khấu hoặc điều chỉnh nếu có
    private String note;
}
