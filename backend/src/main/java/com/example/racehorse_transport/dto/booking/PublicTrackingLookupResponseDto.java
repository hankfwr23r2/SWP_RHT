package com.example.racehorse_transport.dto.booking;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PublicTrackingLookupResponse")
public record PublicTrackingLookupResponseDto(String code, PublicTrackingResponseDto result) {}
