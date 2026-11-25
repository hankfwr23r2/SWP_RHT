package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.health.*;
import com.example.racehorse_transport.service.HealthCheckTaskService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/health-check-tasks")
@RequiredArgsConstructor
public class HealthCheckTaskController {
    private final HealthCheckTaskService tasks;

    @PostMapping
    @PreAuthorize("hasRole('COORDINATOR')")
    public ApiResponseDto<HealthCheckTaskResponseDto> assignHealthCheckTask(@Valid @RequestBody HealthCheckTaskCreateRequestDto request,
                                                           Authentication auth) {
        return ApiResponseDto.success(tasks.assign(request, auth.getName()), "Assigned");
    }

    @GetMapping("/assignees")
    @PreAuthorize("hasRole('COORDINATOR')")
    public ApiResponseDto<List<HealthCheckAssigneeResponseDto>> listHealthCheckAssignees(@RequestParam Long bookingHorseId,
            @RequestParam(required = false) Long vehicleTripId, @RequestParam(required = false) Long horseStayId) {
        return ApiResponseDto.success(tasks.assignees(bookingHorseId, vehicleTripId, horseStayId), "Eligible escorts");
    }

    @GetMapping
    @PreAuthorize("hasRole('COORDINATOR')")
    public ApiResponseDto<List<HealthCheckTaskResponseDto>> listHorseHealthCheckTasks(@RequestParam Long bookingHorseId, Authentication auth) {
        return ApiResponseDto.success(tasks.ofHorse(bookingHorseId, auth.getName()), "Health check tasks");
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('ESCORT')")
    public ApiResponseDto<List<HealthCheckTaskResponseDto>> listMyHealthCheckTasks(Authentication auth) {
        return ApiResponseDto.success(tasks.mine(auth.getName()), "My health check tasks");
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasRole('ESCORT')")
    public ApiResponseDto<HealthCheckTaskResponseDto> acceptHealthCheckTask(@PathVariable Long id, Authentication auth) {
        return ApiResponseDto.success(tasks.accept(id, auth.getName()), "Accepted");
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('COORDINATOR')")
    public ApiResponseDto<HealthCheckTaskResponseDto> cancelHealthCheckTask(@PathVariable Long id,
            @Valid @RequestBody HealthCheckTaskCancelRequestDto request, Authentication auth) {
        return ApiResponseDto.success(tasks.cancel(id, request, auth.getName()), "Cancelled");
    }
}
