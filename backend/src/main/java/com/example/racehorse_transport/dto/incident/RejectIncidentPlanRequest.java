package com.example.racehorse_transport.dto.incident;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejectIncidentPlanRequest {

    @NotBlank(message = "Lý do trả phương án không được để trống")
    private String reason;
}
