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
    private String by; // controller ghi đè bằng tài khoản đang đăng nhập
    private List<QuoteAdjustmentDto> adjustments;
    private List<QuoteLineDto> lines;
    // Không dùng nữa: backend tự tính từ lines và adjustments (QuoteTotals); giữ để frontend cũ gửi lên không lỗi
    private BigDecimal subtotal;
    private BigDecimal total;
    private BigDecimal deposit;
    private BigDecimal balance;
}
