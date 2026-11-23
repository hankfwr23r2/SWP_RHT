package com.example.racehorse_transport.dto.incident;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@Schema(name = "ConfirmIncidentFitRequest")
public class ConfirmIncidentFitRequestDto {
    @NotBlank private String photo;
    @DecimalMin("0.0") private BigDecimal waterLiters = BigDecimal.ZERO;
    private boolean hay;
    // Hộ tống xác nhận đủ sức trước khi hết giờ nghỉ đã duyệt ở trạm SOS (demo không bắt chờ)
    private boolean confirmEarly;

    @NotEmpty @Valid
    private List<CareRecord> horses;

    @Data
    @NoArgsConstructor
    public static class CareRecord {
        @NotBlank private String horseId;
        @NotBlank @Size(max = 2000) private String careNote;
        @DecimalMin("0.0") @DecimalMax("60.0") private BigDecimal temperature;
    }
}
