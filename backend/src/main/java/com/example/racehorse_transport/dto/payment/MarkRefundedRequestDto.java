package com.example.racehorse_transport.dto.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MarkRefundedRequestDto(@NotBlank @Size(max = 100) String transferReference) {}
