package com.example.racehorse_transport.dto.operation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Không nhận một con khách tự chở tới hub (chip sai, không đủ sức khỏe), bắt buộc có lý do
public record HubRefusalRequestDto(@NotBlank @Size(max = 500) String reason) {}
