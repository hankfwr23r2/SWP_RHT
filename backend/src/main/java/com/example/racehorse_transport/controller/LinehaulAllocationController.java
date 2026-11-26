package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.dispatch.LinehaulApplyRequestDto;
import com.example.racehorse_transport.dto.dispatch.LinehaulSuggestionResponseDto;
import com.example.racehorse_transport.service.LinehaulAllocationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/departures/{departureId}")
@RequiredArgsConstructor
@PreAuthorize("hasRole('COORDINATOR')")
public class LinehaulAllocationController {
    private final LinehaulAllocationService allocations;

    @GetMapping("/linehaul-suggestion")
    public ApiResponseDto<LinehaulSuggestionResponseDto> suggestLinehaul(@PathVariable Long departureId) {
        return ApiResponseDto.success(allocations.suggest(departureId), "Gợi ý chia xe trục");
    }

    @PostMapping("/linehaul-trips")
    public ApiResponseDto<List<Long>> applyLinehaul(@PathVariable Long departureId, @Valid @RequestBody LinehaulApplyRequestDto request,
                                                    Authentication actor) {
        return ApiResponseDto.success(allocations.apply(departureId, request, actor.getName()), "Đã lập các lượt trục");
    }
}
