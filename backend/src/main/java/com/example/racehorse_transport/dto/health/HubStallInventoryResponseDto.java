package com.example.racehorse_transport.dto.health;

import java.util.List;

public record HubStallInventoryResponseDto(List<HubStallResponseDto> stalls, int available, int total) {}
