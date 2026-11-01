package com.example.racehorse_transport.dto.payment;
import lombok.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ReconcileInsuranceRequest {
    @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 0) private BigDecimal transferredAmount;
    @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 0) private BigDecimal providerRefundAmount;
    @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 0) private BigDecimal customerRefundAmount;
    @NotBlank @Size(max = 100) private String reference;
    @NotBlank @Size(max = 200) private String note;
}
