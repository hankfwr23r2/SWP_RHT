package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.dispatch.VehicleTripCancelRequestDto;
import com.example.racehorse_transport.dto.dispatch.VehicleTripCreateRequestDto;
import com.example.racehorse_transport.dto.dispatch.VehicleTripResponseDto;
import com.example.racehorse_transport.dto.vehicle.VehicleResponseDto;
import java.time.Instant;
import java.util.List;

// Điều độ: lượt xe và điểm dừng theo kế hoạch. Chủ sở hữu vehicle_trip, trip_stop (kế hoạch): luồng B (B1).
public interface DispatchService {

    // Lượt xe có thời gian chạy giao với [from, to)
    List<VehicleTripResponseDto> trips(Instant from, Instant to);

    VehicleTripResponseDto trip(Long id);

    // LINEHAUL chép điểm dừng từ tuyến của chuyến; loại khác lấy điểm dừng điều phối gửi lên
    VehicleTripResponseDto createTrip(VehicleTripCreateRequestDto request);

    // Hủy lượt chưa chạy và chưa có ngựa; gỡ tổ lái khỏi lượt
    VehicleTripResponseDto cancelTrip(Long id, VehicleTripCancelRequestDto request);

    // Xe để chọn khi lập lượt (kèm trạng thái, xe không ACTIVE bị khóa ở giao diện)
    List<VehicleResponseDto> vehicles();
}
