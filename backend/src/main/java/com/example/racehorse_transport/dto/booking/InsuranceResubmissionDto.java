package com.example.racehorse_transport.dto.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class InsuranceResubmissionDto {
    @NotBlank private String horseId;
    @NotNull @Valid private HorseInsuranceDto insurance;
    // Bắt buộc khi khách không bổ sung được và đổi sang loại bảo hiểm khác.
    @Size(max = 500) private String switchReason;
    private Boolean limitedLiabilityAccepted;
}
