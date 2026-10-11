package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.booking.BookingResponse;
import com.example.racehorse_transport.dto.coordinator.CoordinatorPlanRequest;
import com.example.racehorse_transport.response.ApiResponse;
import com.example.racehorse_transport.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/coordinator/bookings")
@RequiredArgsConstructor
public class CoordinatorBookingController {

    private final BookingService bookingService;

    // 1. Điều phối viên chốt xe và lộ trình đơn hàng
    @PostMapping("/{id}/fleet-plan")
    public ResponseEntity<ApiResponse<BookingResponse>> confirmFleetPlan(
            @PathVariable("id") String id,
            @Valid @RequestBody CoordinatorPlanRequest request) {
        BookingResponse response = bookingService.confirmCoordinatorPlan(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Chốt xe và lộ trình thành công. Đã chuyển quản lý duyệt báo giá!"));
    }

    // 2. Điều phối viên xem danh sách các đơn cần điều phối (hoặc lọc theo status)
    @GetMapping
    public ResponseEntity<ApiResponse<List<BookingResponse>>> getCoordinatorBookings(
            @RequestParam(value = "status", required = false, defaultValue = "ALL") String status) {
        List<BookingResponse> bookings = bookingService.getBookingsByStatus(status);
        return ResponseEntity.ok(ApiResponse.success(bookings, "Lấy danh sách đơn hàng điều phối thành công"));
    }

    // 3. Điều phối viên xem chi tiết một đơn hàng
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BookingResponse>> getBookingDetail(
            @PathVariable("id") String id) {
        Integer bookingId;
        try {
            if (id.toUpperCase().startsWith("BK-")) {
                bookingId = Integer.parseInt(id.substring(3).trim());
            } else {
                bookingId = Integer.parseInt(id.trim());
            }
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Mã đơn không hợp lệ"));
        }
        BookingResponse response = bookingService.getBookingById(bookingId, null);
        return ResponseEntity.ok(ApiResponse.success(response, "Lấy chi tiết đơn hàng thành công"));
    }
}
