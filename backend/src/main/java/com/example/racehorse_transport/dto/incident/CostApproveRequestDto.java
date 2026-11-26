package com.example.racehorse_transport.dto.incident;

import jakarta.validation.constraints.Size;

public record CostApproveRequestDto(@Size(max = 500) String emergencyOverrideReason) {}
