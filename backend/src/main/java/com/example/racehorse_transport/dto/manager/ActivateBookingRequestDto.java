package com.example.racehorse_transport.dto.manager;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ActivateBookingRequest")
public class ActivateBookingRequestDto {
    private String by;
    private Map<String, Object> specialist;
    private Map<String, Object> coordinator;
}
