package com.example.racehorse_transport.dto.incident;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

public record PhoneCostConsentRequestDto(@NotBlank @Size(max = 500) String note) {}
