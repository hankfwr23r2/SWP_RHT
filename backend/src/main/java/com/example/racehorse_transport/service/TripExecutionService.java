package com.example.racehorse_transport.service;

// Thực hiện lượt xe (B2): xuất phát, tới và rời điểm dừng, kết thúc. Chủ sở hữu giờ thực tế của vehicle_trip, trip_stop.
// Trả về id lượt xe để nơi gọi lấy lại màn hình của tổ xe.
public interface TripExecutionService {

    // Kiểm tài xế đã nhận việc, đủ giấy (DocumentCompletenessService) và bảo hiểm CONFIRMED của các đơn đón trên lượt
    void start(Long vehicleTripId, String username);

    Long arrive(Long tripStopId, String username);

    // Rời nơi nhận ngựa: mọi đơn có ngựa lên xe ở đây phải đã trả đủ (#32), rồi đơn sang IN_OPERATION
    Long depart(Long tripStopId, String username);

    void complete(Long vehicleTripId, String username);
}
