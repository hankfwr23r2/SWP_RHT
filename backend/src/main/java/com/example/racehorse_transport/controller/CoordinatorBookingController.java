package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.booking.BookingResponseDto;
import com.example.racehorse_transport.dto.coordinator.CoordinatorPlanRequestDto;
import com.example.racehorse_transport.dto.manager.RejectBookingRequestDto;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.service.BookingQueryService;
import com.example.racehorse_transport.service.BookingService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/coordinator/bookings")
@PreAuthorize("hasAnyRole('COORDINATOR', 'MANAGER', 'ADMIN')")
@RequiredArgsConstructor
public class CoordinatorBookingController {

    private final BookingService bookingService;
    private final BookingQueryService bookingQueries;

    // 1. Điều phối viên chốt xe và lộ trình đơn hàng
    @PostMapping("/{id}/fleet-plan")
    public ResponseEntity<ApiResponseDto<BookingResponseDto>> confirmFleetPlan(
            @PathVariable("id") String id,
            @Valid @RequestBody CoordinatorPlanRequestDto request) {
        BookingResponseDto response = bookingService.confirmCoordinatorPlan(id, request);
        return ResponseEntity.ok(ApiResponseDto.success(response, "Chốt xe và lộ trình thành công. Đã chuyển quản lý duyệt báo giá!"));
    }

    // 2. Điều phối viên xem danh sách các đơn cần điều phối (hoặc lọc theo status)
    @GetMapping
    public ResponseEntity<ApiResponseDto<List<BookingResponseDto>>> getCoordinatorBookings(
            @RequestParam(value = "status", required = false, defaultValue = "ALL") String status) {
        List<BookingResponseDto> bookings = bookingService.getBookingsByStatus(status);
        return ResponseEntity.ok(ApiResponseDto.success(bookings, "Lấy danh sách đơn hàng điều phối thành công"));
    }

    // 3. Điều phối viên xem chi tiết một đơn hàng
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDto<BookingResponseDto>> getBookingDetail(
            @PathVariable("id") String id) {
        BookingResponseDto response = bookingService.getBookingById(bookingQueries.resolveBookingId(id), null);
        return ResponseEntity.ok(ApiResponseDto.success(response, "Lấy chi tiết đơn hàng thành công"));
    }

    // 4. Điều phối viên từ chối đơn hàng (không xếp được xe / lộ trình)
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponseDto<BookingResponseDto>> rejectBooking(
            @PathVariable("id") String id,
            @RequestBody RejectBookingRequestDto request,
            Authentication authentication) {
        // Người từ chối và vai trò lấy từ token, không tin body: vai trò quyết định đơn ở bước nào được từ chối
        request.setBy(authentication.getName());
        request.setRole("coordinator");
        BookingResponseDto response = bookingService.rejectBooking(id, request);
        return ResponseEntity.ok(ApiResponseDto.success(response, "Đã từ chối đơn hàng thành công"));
    }
}
