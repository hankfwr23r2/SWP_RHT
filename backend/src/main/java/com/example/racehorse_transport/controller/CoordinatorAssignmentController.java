package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.dispatch.CoordinatorAssignmentRequestDto;
import com.example.racehorse_transport.dto.dispatch.CoordinatorTakeOverRequestDto;
import com.example.racehorse_transport.service.CoordinatorAssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class CoordinatorAssignmentController {
    private final CoordinatorAssignmentService assignments;
    private final com.example.racehorse_transport.service.CoordinatorTripService trips;

    @GetMapping("/coordinator/vehicle-trips")
    @PreAuthorize("hasRole('COORDINATOR')")
    public ApiResponseDto<java.util.List<com.example.racehorse_transport.dto.dispatch.CoordinatorTripResponseDto>> mine(Authentication actor) {
        return ApiResponseDto.success(trips.mine(actor.getName()), "Lượt xe của bạn.");
    }

    @GetMapping("/coordinator/vehicle-trips/{id}/horses")
    @PreAuthorize("hasRole('COORDINATOR')")
    public ApiResponseDto<java.util.List<com.example.racehorse_transport.dto.dispatch.CoordinatorTripHorseResponseDto>> horses(
            @PathVariable Long id, Authentication actor) {
        return ApiResponseDto.success(trips.horses(id, actor.getName()), "Ngựa trên lượt xe.");
    }

    @PostMapping("/departures/{id}/assign-coordinator")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponseDto<Void> assignDeparture(@PathVariable Long id,
            @Valid @RequestBody CoordinatorAssignmentRequestDto request, Authentication actor) {
        assignments.assignDeparture(id, request, actor.getName());
        return ApiResponseDto.success(null, "Đã phân công điều phối.");
    }

    @PostMapping("/vehicle-trips/{id}/assign-coordinator")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponseDto<Void> assignTrip(@PathVariable Long id,
            @Valid @RequestBody CoordinatorAssignmentRequestDto request, Authentication actor) {
        assignments.assignTrip(id, request, actor.getName());
        return ApiResponseDto.success(null, "Đã phân công điều phối.");
    }

    @PostMapping("/vehicle-trips/{id}/take-over")
    @PreAuthorize("hasRole('COORDINATOR')")
    public ApiResponseDto<Void> takeOver(@PathVariable Long id,
            @Valid @RequestBody CoordinatorTakeOverRequestDto request, Authentication actor) {
        assignments.takeOver(id, request, actor.getName());
        return ApiResponseDto.success(null, "Đã nhận phụ trách lượt xe.");
    }
}
