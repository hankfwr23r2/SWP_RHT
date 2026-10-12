package com.example.racehorse_transport.dto.settlement;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseItemDto {
    private String id;
    private String category; // "vet_fee", "medicine", "holding_stable", "rescue", "extra_service"
    private String description;
    private BigDecimal amount;
    private String photo; // URL ảnh chụp hóa đơn từ Cloudinary (Evidence-First)
    private String payer; // "customer" (Khách chịu) hoặc "carrier" (Nhà xe chịu)
}
