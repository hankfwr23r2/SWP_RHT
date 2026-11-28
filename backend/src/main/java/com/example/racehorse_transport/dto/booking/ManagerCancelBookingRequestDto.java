package com.example.racehorse_transport.dto.booking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record ManagerCancelBookingRequestDto(@NotBlank @Size(max = 500) String reason,
        @NotNull @Size(max = 200) List<@NotNull @Positive Long> unspentQuotationLineIds) {}
