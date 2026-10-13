package com.example.racehorse_transport.dto.manager;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejectBookingRequest {
    private String by;
    private String role; // manager or coordinator
    private String reason;
}
