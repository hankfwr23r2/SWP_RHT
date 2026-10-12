package com.example.racehorse_transport.dto.payment;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelBookingRequest {
    private String reason;
    private String cancelledBy;
}
