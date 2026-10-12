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
    private String waybillNo;
    private String status;
    private BigDecimal totalBasePaid; // Cước cố định đã trả (cọc 30% + 70%)
    private List<ExpenseItemDto> items;
    private BigDecimal totalExtraAmount; // Khoản phát sinh thuộc phần khách chịu
    private Long issuedAt;
    private Long dueAt; // Thời hạn thanh toán (24 giờ sau khi phát hành)
    private String issuedBy;
    private boolean paid;
    private Object paidInfo;
    private RatingDto rating;
}
