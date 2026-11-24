package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.health.HorseStayResponseDto;
import com.example.racehorse_transport.dto.operation.HubHandoverRequestDto;

// Giao nhận tại hub với khách tự chở (B2, pickup_method hoặc delivery_method SELF): handover gắn horse_stay, không có chặng xe tương ứng
public interface HubHandoverService {

    // Nhận ngựa khách tự chở tới theo lịch hẹn: kiểm bảo hiểm, giấy bắt buộc, chip; mở lưu trú tập kết; lập khoản số dư
    HorseStayResponseDto receive(Long horseStayId, HubHandoverRequestDto request, String username);

    // Trả ngựa cho người nhận tới hub: ngựa đã tới hub bằng chặng cuối; đóng lưu trú; con cuối của đơn thì đơn DELIVERED (#16)
    HorseStayResponseDto deliver(Long horseStayId, HubHandoverRequestDto request, String username);
}
