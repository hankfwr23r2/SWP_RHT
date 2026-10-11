package com.example.racehorse_transport.dto.booking;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejectQuoteRequest {
    private String reason;
}
