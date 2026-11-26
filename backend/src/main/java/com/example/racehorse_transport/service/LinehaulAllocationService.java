package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.dispatch.LinehaulApplyRequestDto;
import com.example.racehorse_transport.dto.dispatch.LinehaulSuggestionResponseDto;
import java.util.List;

public interface LinehaulAllocationService {
    LinehaulSuggestionResponseDto suggest(Long departureId);

    List<Long> apply(Long departureId, LinehaulApplyRequestDto request, String username);
}
