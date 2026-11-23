package com.example.racehorse_transport.dto.settlement;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "IssueSettlementRequest")
public class IssueSettlementRequestDto {
    private String managerName;
    private List<ExpenseItemDto> items;
    private Map<String, String> payers; // Map[itemId -> "customer" | "carrier"]
    private BigDecimal adjustment; // Chiết khấu hoặc điều chỉnh nếu có
    private String note;
}
