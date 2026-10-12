package com.example.racehorse_transport.dto.field;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueSettlementRequest {

    private String managerUsername;

    private BigDecimal extraCost; // Tổng khoản phát sinh khách phải thanh toán thêm

    private List<SettlementLineDto> lines;

    private String note;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SettlementLineDto {
        private String label;
        private BigDecimal amount;
        private String invoiceUrl;
        private String sourceIncident;
    }
}
