package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.incident.HandoverCauseRequestDto;
import com.example.racehorse_transport.dto.incident.IncidentResponseDto;
import com.example.racehorse_transport.service.DeliveryFaultService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DeliveryFaultController {
    private final DeliveryFaultService deliveryFaults;

    @PostMapping("/incidents/{id}/handover-cause")
    @PreAuthorize("hasAnyRole('MANAGER', 'COORDINATOR')")
    public ApiResponseDto<IncidentResponseDto> amendHandoverCause(@PathVariable Long id,
            @Valid @RequestBody HandoverCauseRequestDto request, Authentication actor) {
        return ApiResponseDto.success(deliveryFaults.amendCause(id, request, actor.getName()), "Đã sửa kết luận giao hụt");
    }
}
