package com.example.racehorse_transport.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Quản lý hủy chuyến: ai chịu lỗi (COMPANY | CUSTOMER) và lý do khách xem được
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CancelTripRequest")
public class CancelTripRequestDto {
    private String fault;
    private String reason;
}
