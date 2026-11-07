package com.example.racehorse_transport.dto.booking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PublicTrackingLookupRequest(
        @NotEmpty @Size(max = 5) List<@NotBlank @Size(max = 64) String> codes,
        @NotBlank @Pattern(regexp = "\\d{4}") String phoneLast4) {}
