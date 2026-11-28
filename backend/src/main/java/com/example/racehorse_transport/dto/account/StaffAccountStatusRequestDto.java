package com.example.racehorse_transport.dto.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StaffAccountStatusRequestDto(@NotBlank @Size(max = 500) String reason) {}
