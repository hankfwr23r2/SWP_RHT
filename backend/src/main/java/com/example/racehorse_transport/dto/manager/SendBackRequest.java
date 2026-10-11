package com.example.racehorse_transport.dto.manager;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendBackRequest {
    private String by;
    private String to; // "specialist" or "coordinator"
    private String reason;
}
