package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.health.StablingClockResponseDto;
import com.example.racehorse_transport.dto.health.StablingCollectionRequestDto;
import com.example.racehorse_transport.dto.health.StablingLaterSlotRequestDto;
import com.example.racehorse_transport.dto.health.StablingReadyRequestDto;
import com.example.racehorse_transport.service.StablingClockService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class StablingClockController {
    private final StablingClockService clock;

    @GetMapping("/bookings/{bookingId}/stabling-clocks")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponseDto<List<StablingClockResponseDto>> forBooking(@PathVariable Long bookingId, Authentication actor) {
        return ApiResponseDto.success(clock.forBooking(bookingId, actor.getName()), "Phí giữ ngựa tại hub.");
    }

    @PostMapping("/horse-stays/{stayId}/ready")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponseDto<Void> ready(@PathVariable Long stayId, Authentication actor) {
        clock.ready(stayId, actor.getName(), null);
        return ApiResponseDto.success(null, "Đã báo sẵn sàng nhận.");
    }

    @PostMapping("/horse-stays/{stayId}/record-ready")
    @PreAuthorize("hasRole('COORDINATOR')")
    public ApiResponseDto<Void> recordReady(@PathVariable Long stayId, @Valid @RequestBody StablingReadyRequestDto request,
            Authentication actor) {
        clock.ready(stayId, actor.getName(), request.callNote());
        return ApiResponseDto.success(null, "Đã ghi cuộc gọi.");
    }

    @PostMapping("/horse-stays/{stayId}/choose-later-slot")
    @PreAuthorize("hasRole('COORDINATOR')")
    public ApiResponseDto<Void> chooseLaterSlot(@PathVariable Long stayId, @Valid @RequestBody StablingLaterSlotRequestDto request) {
        clock.laterSlot(stayId, request.earliestAt(), request.chosenAt());
        return ApiResponseDto.success(null, "Đã ghi khung giao khách chọn.");
    }

    @PostMapping("/horse-stays/{stayId}/report-collection")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponseDto<Void> reportCollection(@PathVariable Long stayId, @Valid @RequestBody StablingCollectionRequestDto request,
            Authentication actor) {
        clock.collect(stayId, actor.getName(), request.expectedAt());
        return ApiResponseDto.success(null, "Đã báo giờ tự nhận.");
    }
}
