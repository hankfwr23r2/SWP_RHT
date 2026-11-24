package com.example.racehorse_transport.dto.operation;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

// Giờ hẹn người nhận tới hub nhận ngựa (delivery_method SELF). Quá giờ này thì gửi chuồng, người nhận trả phí
public record HubCollectionTimeRequestDto(@NotNull Instant collectAt) {}
