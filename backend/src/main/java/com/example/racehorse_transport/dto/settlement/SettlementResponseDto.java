package com.example.racehorse_transport.dto.settlement;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "SettlementResponse")
public class SettlementResponseDto {
    private String bookingCode;
    private String status;
    private List<ExpenseItemDto> items;
    private BigDecimal totalCompanyCost;
    private Long reviewedAt;
    private String reviewedBy;
}
