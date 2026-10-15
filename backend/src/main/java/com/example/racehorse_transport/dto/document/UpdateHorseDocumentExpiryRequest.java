package com.example.racehorse_transport.dto.document;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateHorseDocumentExpiryRequest {
    @NotNull(message = "Ngày hết hạn không được để trống.")
    private LocalDate expiryDate;
}
