package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.health.HorseStayResponseDto;
import org.springframework.web.multipart.MultipartFile;
import com.example.racehorse_transport.dto.operation.HubHandoverRequestDto;
import com.example.racehorse_transport.dto.operation.HandoverPaperResponseDto;
import java.util.List;
import com.example.racehorse_transport.dto.operation.HubRefusalRequestDto;

// Giao nhận tại hub với khách tự chở (B2, pickup_method hoặc delivery_method SELF): handover gắn horse_stay, không có chặng xe tương ứng
public interface HubHandoverService {

    List<HandoverPaperResponseDto> papers(Long horseStayId, boolean receiving);

    // Nhận ngựa khách tự chở tới theo lịch hẹn: kiểm bảo hiểm, giấy bắt buộc, chip; mở lưu trú tập kết; lập khoản số dư
    HorseStayResponseDto receive(Long horseStayId, HubHandoverRequestDto request, MultipartFile photo, MultipartFile record, String username);

    // Không nhận một con khách tự chở tới theo lịch hẹn: loại khỏi đơn, hủy chặng, bỏ lịch hẹn
    void refuse(Long horseStayId, HubRefusalRequestDto request, String username);

    // Trả ngựa cho người nhận tới hub: ngựa đã tới hub bằng chặng cuối; đóng lưu trú; con cuối của đơn thì đơn DELIVERED (#16)
    HorseStayResponseDto deliver(Long horseStayId, HubHandoverRequestDto request, MultipartFile photo, MultipartFile record, String username);
}
