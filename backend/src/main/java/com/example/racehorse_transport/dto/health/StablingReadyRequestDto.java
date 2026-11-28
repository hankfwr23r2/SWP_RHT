package com.example.racehorse_transport.dto.health;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StablingReadyRequestDto(@NotBlank @Size(max = 500) String callNote) {}
