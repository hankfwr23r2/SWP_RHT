package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.booking.BookingResponse;
import com.example.racehorse_transport.response.ApiResponse;
import com.example.racehorse_transport.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/field/bookings")
@PreAuthorize("hasAnyRole('DRIVER', 'ESCORT', 'COORDINATOR', 'MANAGER', 'ADMIN')")
@RequiredArgsConstructor
public class FieldBookingController {

    private final BookingService bookingService;

    // 1. Kíp xe (Tài xế / Hộ tống) lấy danh sách các đơn vận chuyển
    @GetMapping
    public ResponseEntity<ApiResponse<List<BookingResponse>>> getFieldBookings(
            @RequestParam(value = "status", required = false, defaultValue = "ALL") String status) {
        List<BookingResponse> bookings = bookingService.getBookingsByStatus(status);
        return ResponseEntity.ok(ApiResponse.success(bookings, "Lấy danh sách đơn hàng cho kíp xe thành công"));
    }

    // 2. Kíp xe xem chi tiết một đơn
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BookingResponse>> getBookingDetail(@PathVariable("id") String id) {
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

    // 3. Kíp xe cập nhật tiến độ chuyến xe (check-in, quét chip, thu hồ sơ, xuất phát, an sinh, hoàn tất)
    @PostMapping("/{id}/trip-update")
    public ResponseEntity<ApiResponse<BookingResponse>> updateTripProgress(
            @PathVariable("id") String id,
            @RequestBody com.example.racehorse_transport.dto.field.TripUpdateRequest request) {
        BookingResponse response = bookingService.updateTripProgress(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Cập nhật tiến độ chuyến xe thành công"));
    }
}
