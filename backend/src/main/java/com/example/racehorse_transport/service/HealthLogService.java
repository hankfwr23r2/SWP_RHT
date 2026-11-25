package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.health.HealthLogRequestDto;
import com.example.racehorse_transport.dto.health.HealthLogResponseDto;
import com.example.racehorse_transport.dto.health.HorseStayResponseDto;
import java.util.List;

// Sổ sức khỏe theo từng con ngựa (B3). Chủ sở hữu horse_health_log.
public interface HealthLogService {

    List<HorseStayResponseDto> myStays(String username);

    List<HealthLogResponseDto> ofHorse(Long bookingHorseId);

    // Ghi tại điểm dừng (người trong tổ lái, con đang trên xe ở điểm đó) hoặc trong lần lưu trú đang diễn ra.
    // Bỏ uống 2 lần dừng liền thì ít nhất WATCH và báo điều phối.
    HealthLogResponseDto record(HealthLogRequestDto request, String username);
}
