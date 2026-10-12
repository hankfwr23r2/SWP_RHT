package com.example.racehorse_transport.dto.specialist;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestResubmissionRequest {
    private String specialistName;
    private String reason;
    private List<ResubmitItemDto> items;
}
