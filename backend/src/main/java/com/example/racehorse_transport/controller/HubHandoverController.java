package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.health.HorseStayResponseDto;
import com.example.racehorse_transport.dto.operation.HubAppointmentRequestDto;
import com.example.racehorse_transport.dto.operation.HubHandoverRequestDto;
import com.example.racehorse_transport.service.HorseStayService;
import com.example.racehorse_transport.service.HubHandoverService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Khách tự chở tới hub hoặc tự nhận ở hub (SELF): điều phối hẹn giờ, nhận và trả ngựa tại hub (MAN_HINH 3.5c)
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('COORDINATOR')")
public class HubHandoverController {

    private final HorseStayService stays;
    private final HubHandoverService hubHandovers;

    @PostMapping("/booking-horses/{id}/hub-appointments")
    public ResponseEntity<ApiResponseDto<HorseStayResponseDto>> appoint(@PathVariable Long id, @Valid @RequestBody HubAppointmentRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(stays.planSelfDropOff(id, request.plannedStartAt()), "Đã hẹn nhận ngựa tại hub"));
    }

    @GetMapping("/horse-stays/appointments")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'MANAGER')")
    public ApiResponseDto<List<HorseStayResponseDto>> appointments(@RequestParam Long locationId) {
        return ApiResponseDto.success(stays.appointmentsAt(locationId), "Lịch hẹn nhận ngựa");
    }

    @PostMapping("/horse-stays/{id}/receipts")
    public ApiResponseDto<HorseStayResponseDto> receive(@PathVariable Long id, Authentication authentication, @Valid @RequestBody HubHandoverRequestDto request) {
        return ApiResponseDto.success(hubHandovers.receive(id, request, authentication.getName()), "Đã nhận ngựa tại hub");
    }

    @PostMapping("/horse-stays/{id}/deliveries")
    public ApiResponseDto<HorseStayResponseDto> deliver(@PathVariable Long id, Authentication authentication, @Valid @RequestBody HubHandoverRequestDto request) {
        return ApiResponseDto.success(hubHandovers.deliver(id, request, authentication.getName()), "Đã trả ngựa cho người nhận");
    }
}
