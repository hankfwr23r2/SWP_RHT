package com.example.racehorse_transport.dto.route;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

// stopLocationIds: các trạm và cửa khẩu theo thứ tự đi (mã trong bảng location); cửa khẩu nhận ra nhờ loại BorderGate
@Schema(name = "RoutePreviewRequest")
public record RoutePreviewRequestDto(
        @NotNull @Valid RoutePlaceDto pickup,
        @NotNull @Valid RoutePlaceDto dropoff,
        List<@NotNull Integer> stopLocationIds,
        @NotBlank String vehicleId,
        @NotNull Long departAt) {
}
