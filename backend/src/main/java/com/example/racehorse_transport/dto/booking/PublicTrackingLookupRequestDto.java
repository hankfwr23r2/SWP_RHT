package com.example.racehorse_transport.dto.booking;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(name = "PublicTrackingLookupRequest")
public record PublicTrackingLookupRequestDto(
        @NotEmpty @Size(max = 5) List<@NotBlank @Size(max = 64) String> codes,
        @NotBlank @Pattern(regexp = "\\d{4}") String phoneLast4) {}
