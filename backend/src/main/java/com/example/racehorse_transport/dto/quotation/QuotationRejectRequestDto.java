package com.example.racehorse_transport.dto.quotation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QuotationRejectRequestDto(@NotBlank @Size(max = 500) String reason) {}
