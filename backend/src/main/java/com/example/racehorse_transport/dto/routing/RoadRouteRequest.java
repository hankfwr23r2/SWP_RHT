package com.example.racehorse_transport.dto.routing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

// Đường xe tải qua một dãy điểm (sự cố trên đường). alternatives: thêm đường tránh khi kẹt xe
public record RoadRouteRequest(
        @NotNull @Size(min = 2, max = 25) List<@NotNull @Valid Point> points,
        boolean alternatives) {

    public record Point(@NotNull Double lat, @NotNull Double lng) {}
}
