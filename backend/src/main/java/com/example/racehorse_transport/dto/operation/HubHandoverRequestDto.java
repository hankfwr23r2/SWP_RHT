package com.example.racehorse_transport.dto.operation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Nhận hoặc trả ngựa tại hub với khách tự chở (SELF): quét chip, ghi tình trạng lúc giao nhận
public record HubHandoverRequestDto(
        @NotBlank @Size(max = 30) String microchip,
        @Size(max = 1000) String conditionNote) {}
