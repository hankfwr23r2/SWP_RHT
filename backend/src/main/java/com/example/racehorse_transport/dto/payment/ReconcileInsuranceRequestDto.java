package com.example.racehorse_transport.dto.payment;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Schema(name = "ReconcileInsuranceRequest")
public class ReconcileInsuranceRequestDto {
    @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 0) private BigDecimal transferredAmount;
    @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 0) private BigDecimal providerRefundAmount;
    @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 0) private BigDecimal customerRefundAmount;
    @NotBlank @Size(max = 100) private String reference;
    @NotBlank @Size(max = 200) private String note;
}
