package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.dispatch.VehicleTripCreateRequestDto;
import com.example.racehorse_transport.dto.dispatch.VehicleTripResponseDto;
import com.example.racehorse_transport.dto.dispatch.VehicleTripUpdateRequestDto;
import com.example.racehorse_transport.dto.dispatch.DispatchVehicleResponseDto;
import java.time.Instant;
import java.util.List;

// Điều độ: lượt xe và điểm dừng theo kế hoạch. Chủ sở hữu vehicle_trip, trip_stop (kế hoạch): luồng B (B1).
public interface DispatchService {

    // Lượt xe có thời gian chạy giao với [from, to)
    List<VehicleTripResponseDto> trips(Instant from, Instant to);

    VehicleTripResponseDto trip(Long id);

    List<VehicleTripResponseDto> ownedTrips(Long staffId);

    // LINEHAUL chép điểm dừng từ tuyến của chuyến; loại khác lấy điểm dừng điều phối gửi lên
    VehicleTripResponseDto createTrip(VehicleTripCreateRequestDto request, String username);

    // Lưu lượt ngoài trục với giờ do bộ lập lịch tính.
    VehicleTripResponseDto createCalculatedTrip(VehicleTripCreateRequestDto request, String username);

    VehicleTripResponseDto updateTrip(Long id, VehicleTripUpdateRequestDto request, String username);

    // Đổi xe do sự cố (B4, TRANSFER_VEHICLE): lượt đang chạy chuyển sang xe khác; ngựa, điểm dừng, tổ lái giữ nguyên.
    // Xe mới ACTIVE, không trùng giờ với lượt khác từ lúc này tới hết lượt, đủ chỗ cho sơ đồ khoang đang dùng
    VehicleTripResponseDto swapVehicle(Long tripId, Long vehicleId, Long actorUserId, String reason, Instant changedAt);

    // Xe để chọn khi lập lượt (kèm trạng thái, xe không ACTIVE bị khóa ở giao diện)
    List<DispatchVehicleResponseDto> vehicles();
}
