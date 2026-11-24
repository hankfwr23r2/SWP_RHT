package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.dispatch.StallPlanApplyRequestDto;
import com.example.racehorse_transport.dto.dispatch.StallPlanResponseDto;
import com.example.racehorse_transport.service.StallPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// Sơ đồ khoang của lượt xe (màn 3.9a): hệ thống tự xếp, điều phối chỉnh; tổ xe chỉ xem
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('COORDINATOR')")
public class StallPlanController {

    private final StallPlanService stallPlans;

    @GetMapping("/vehicle-trips/{id}/stall-plan")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'MANAGER', 'DRIVER', 'ESCORT')")
    public ApiResponseDto<StallPlanResponseDto> plan(@PathVariable Long id) {
        return ApiResponseDto.success(stallPlans.plan(id), "Sơ đồ khoang");
    }

    @PostMapping("/vehicle-trips/{id}/stall-plan/suggest")
    public ApiResponseDto<StallPlanResponseDto> suggest(@PathVariable Long id) {
        return ApiResponseDto.success(stallPlans.suggest(id), "Sơ đồ xếp lại tự động (chưa lưu)");
    }

    @PostMapping("/vehicle-trips/{id}/stall-plan/apply")
    public ApiResponseDto<StallPlanResponseDto> apply(@PathVariable Long id, @Valid @RequestBody StallPlanApplyRequestDto request) {
        return ApiResponseDto.success(stallPlans.apply(id, request), "Đã lưu sơ đồ khoang");
    }
}
