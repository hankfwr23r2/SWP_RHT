package com.example.racehorse_transport.dto.booking;

import jakarta.validation.constraints.NotBlank;

public record InsuranceReopenRequestDto(@NotBlank String reason) {}
