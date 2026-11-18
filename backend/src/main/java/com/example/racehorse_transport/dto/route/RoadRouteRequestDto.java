package com.example.racehorse_transport.dto.route;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

// Đường xe tải qua một dãy điểm (sự cố trên đường). alternatives: thêm đường tránh khi kẹt xe
@Schema(name = "RoadRouteRequest")
public record RoadRouteRequestDto(
        @NotNull @Size(min = 2, max = 25) List<@NotNull @Valid Point> points,
        boolean alternatives) {

    public record Point(@NotNull Double lat, @NotNull Double lng) {}
}
