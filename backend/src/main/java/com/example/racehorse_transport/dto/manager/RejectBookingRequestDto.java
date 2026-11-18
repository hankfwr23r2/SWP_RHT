package com.example.racehorse_transport.dto.manager;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "RejectBookingRequest")
public class RejectBookingRequestDto {
    private String by;
    private String role; // manager or coordinator
    private String reason;
}
