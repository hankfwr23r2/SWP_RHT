package com.example.racehorse_transport.dto.booking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicTrackingResponse {
    private String route;
    private String depart;
    private Integer step;
    private Boolean done;
    private String status;
    private String tone;
    private Map<String, Object> now;
    private String note;
}
