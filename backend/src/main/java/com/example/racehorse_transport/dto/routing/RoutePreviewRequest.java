package com.example.racehorse_transport.dto.routing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

// stopLocationIds: các trạm và cửa khẩu theo thứ tự đi (mã trong bảng location); cửa khẩu nhận ra nhờ loại BorderGate
public record RoutePreviewRequest(
        @NotNull @Valid RoutePlace pickup,
        @NotNull @Valid RoutePlace dropoff,
        List<@NotNull Integer> stopLocationIds,
        @NotBlank String vehicleId,
        @NotNull Long departAt) {
}
