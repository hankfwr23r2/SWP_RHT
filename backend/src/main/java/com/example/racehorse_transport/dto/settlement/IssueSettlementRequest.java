package com.example.racehorse_transport.dto.settlement;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueSettlementRequest {
    private String managerName;
    private List<ExpenseItemDto> items;
    private Map<String, String> payers; // Map[itemId -> "customer" | "carrier"]
    private BigDecimal adjustment; // Chiết khấu hoặc điều chỉnh nếu có
    private String note;
}
