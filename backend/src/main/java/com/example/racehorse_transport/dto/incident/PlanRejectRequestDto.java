package com.example.racehorse_transport.dto.incident;

import jakarta.validation.constraints.Size;

public record PlanRejectRequestDto(@Size(max = 2000) String reason) {}
