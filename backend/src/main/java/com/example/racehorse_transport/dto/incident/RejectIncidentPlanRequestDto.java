package com.example.racehorse_transport.dto.incident;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "RejectIncidentPlanRequest")
public class RejectIncidentPlanRequestDto {

    @NotBlank(message = "Lý do trả phương án không được để trống")
    private String reason;
}
