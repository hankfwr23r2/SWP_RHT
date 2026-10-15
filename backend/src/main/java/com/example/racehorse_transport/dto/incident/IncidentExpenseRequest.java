package com.example.racehorse_transport.dto.incident;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentExpenseRequest {

    @NotBlank(message = "Loại chi phí không được để trống")
    private String category;

    @NotBlank(message = "Nội dung chi phí không được để trống")
    private String label;

    @NotBlank(message = "Ảnh hóa đơn không được để trống")
    private String photo;

    @NotNull(message = "Số tiền không được để trống")
    @Positive(message = "Số tiền phải lớn hơn 0")
    private BigDecimal amount;

    @NotBlank(message = "Bên chịu chi phí không được để trống")
    private String payer;
}
