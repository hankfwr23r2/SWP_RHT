package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.health.HorseStayResponseDto;
import java.util.List;

public interface HorseStayQueryService {
    List<HorseStayResponseDto> activeAt(Long locationId);
}
