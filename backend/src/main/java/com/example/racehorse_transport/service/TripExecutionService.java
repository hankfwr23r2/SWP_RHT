package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.operation.TripDepartureReadinessResponseDto;

// Thực hiện lượt xe (B2): xuất phát, tới và rời điểm dừng.
// Trả về id lượt xe để nơi gọi lấy lại màn hình của tổ xe.
public interface TripExecutionService {

    TripDepartureReadinessResponseDto departureReadiness(Long vehicleTripId, String username);

    // Kiểm tài xế đã nhận việc, đủ giấy (DocumentCompletenessService) và bảo hiểm CONFIRMED của các đơn đón trên lượt
    void start(Long vehicleTripId, String username, String earlyReason);

    Long arrive(Long tripStopId, String username);

    // Kiểm qua StopReadinessService (giao nhận dở, #32, #34); rời nơi nhận ngựa thì đơn sang IN_OPERATION.
    // earlyReason: lý do rời trạm nghỉ trước khi nghỉ đủ, có thể null
    Long depart(Long tripStopId, String earlyReason, String username);
}
