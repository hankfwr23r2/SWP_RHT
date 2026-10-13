package com.example.racehorse_transport.dto.manager;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivateBookingRequest {
    private String by;
    private Map<String, Object> specialist;
    private Map<String, Object> coordinator;
}
