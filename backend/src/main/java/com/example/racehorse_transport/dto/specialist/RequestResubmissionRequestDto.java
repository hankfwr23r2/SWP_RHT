package com.example.racehorse_transport.dto.specialist;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "RequestResubmissionRequest")
public class RequestResubmissionRequestDto {
    private String specialistName;
    private String reason;
    private List<ResubmitItemDto> items;
}
