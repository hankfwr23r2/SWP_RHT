package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.booking.*;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.service.BookingAssignmentService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class BookingAssignmentController {
    private final BookingAssignmentService assignments;

    @PostMapping("/bookings/{id}/reassign-specialist")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponseDto<BookingResponseDto> reassign(@PathVariable Long id,
            @Valid @RequestBody BookingReassignmentRequestDto request, Authentication actor) {
        return ApiResponseDto.success(assignments.reassign(id, request, actor.getName()), "Đã cập nhật phân công.");
    }

    @GetMapping("/staff-workloads")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponseDto<List<StaffWorkloadResponseDto>> workloads(@RequestParam String role, Authentication actor) {
        return ApiResponseDto.success(assignments.workloads(role, actor.getName()), "Thành công.");
    }

    @GetMapping("/staff-workloads/{staffId}/bookings")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponseDto<List<BookingResponseDto>> bookings(@PathVariable Long staffId, Authentication actor) {
        return ApiResponseDto.success(assignments.assignedBookings(staffId, actor.getName()), "Thành công.");
    }
}
