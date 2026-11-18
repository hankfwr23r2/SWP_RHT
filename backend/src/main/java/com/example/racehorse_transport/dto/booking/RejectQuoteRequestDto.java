package com.example.racehorse_transport.dto.booking;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "RejectQuoteRequest")
public class RejectQuoteRequestDto {
    private String reason;
}
