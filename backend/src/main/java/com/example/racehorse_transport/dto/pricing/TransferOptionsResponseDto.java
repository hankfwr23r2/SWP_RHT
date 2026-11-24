package com.example.racehorse_transport.dto.pricing;

import com.example.racehorse_transport.policy.PricingPolicy.TransferStatus;

// Đầu không có chặng công ty chở (đơn chạy thẳng, đầu ở nước ngoài) thì NULL
public record TransferOptionsResponseDto(TransferEndDto pickup, TransferEndDto delivery) {

    // reason: lý do hiện cho khách, NULL khi chở được
    public record TransferEndDto(boolean companyAllowed, TransferStatus status, String reason) {

        public static TransferEndDto from(TransferStatus status, String reason) {
            return new TransferEndDto(status.companyAllowed(), status, reason);
        }
    }
}
