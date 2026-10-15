package com.example.racehorse_transport.dto.field;

import lombok.*;

import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripUpdateRequest {
    private String tripId;
    private String action;
    private String actor;
    private Map<String, Object> payload;
}
