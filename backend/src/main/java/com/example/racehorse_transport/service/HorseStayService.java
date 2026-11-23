package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.health.HorseStayResponseDto;
import com.example.racehorse_transport.entity.HorseLeg;
import java.time.Instant;
import java.util.List;

// Thời gian ngựa ở một nơi, không trên xe (B3). Chủ sở hữu horse_stay.
public interface HorseStayService {

    // Giao vào hub xong: mở lưu trú tập kết ở hub, dự kiến tới khi chặng sau bắt đầu
    void openAssembly(HorseLeg alightedLeg, Instant at);

    // Nhận ra khỏi hub hoặc chuồng: đóng lưu trú đang diễn ra của con ngựa
    void closeActive(Long bookingHorseId, Instant at);

    List<HorseStayResponseDto> activeAt(Long locationId);
}
