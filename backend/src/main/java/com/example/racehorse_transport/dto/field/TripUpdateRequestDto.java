package com.example.racehorse_transport.dto.field;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "TripUpdateRequest")
public class TripUpdateRequestDto {
    private String tripId;
    private String action;
    private String actor;
    private Map<String, Object> payload;
}
