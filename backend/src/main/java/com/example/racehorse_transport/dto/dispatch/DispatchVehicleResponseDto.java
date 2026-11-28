package com.example.racehorse_transport.dto.dispatch;

import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Vehicle;
import com.example.racehorse_transport.entity.enums.VehicleStatus;
import java.time.Instant;

// Xe cho bảng điều độ. currentLocation: nơi lượt cuối cùng của xe kết thúc (chưa có lượt thì hub gốc), lượt mới phải bắt đầu ở đó;
// freeFrom: giờ lượt đó xong, null khi chưa có lượt
public record DispatchVehicleResponseDto(Long id, String licensePlate, Integer standardStallCount, Integer wideStallCount,
                                         VehicleStatus status, Long homeHubLocationId, String homeHubLocationCode, Long currentLocationId,
                                         String currentLocationCode, String currentLocationName, Instant freeFrom) {

    public static DispatchVehicleResponseDto from(Vehicle vehicle, Location current, Instant freeFrom) {
        return new DispatchVehicleResponseDto(vehicle.getId(), vehicle.getLicensePlate(), vehicle.getStandardStallCount(),
                vehicle.getWideStallCount(), vehicle.getStatus(), vehicle.getHomeHubLocation().getId(),
                vehicle.getHomeHubLocation().getCode(), current.getId(),
                current.getCode(), current.getName(), freeFrom);
    }
}
