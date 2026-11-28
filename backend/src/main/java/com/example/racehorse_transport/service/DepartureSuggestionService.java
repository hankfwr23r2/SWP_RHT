package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.departure.DepartureSuggestionQueryDto;
import com.example.racehorse_transport.dto.departure.DepartureSuggestionResponseDto;

// Gợi ý cách chạy và chuyến phù hợp cho đơn mới (GĐ1 bước 5). Chỉ đọc. Luồng A (A1).
public interface DepartureSuggestionService {

    DepartureSuggestionResponseDto suggest(DepartureSuggestionQueryDto query);
}
