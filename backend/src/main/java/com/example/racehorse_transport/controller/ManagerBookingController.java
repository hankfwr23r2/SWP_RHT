package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.booking.BookingResponse;
import com.example.racehorse_transport.response.ApiResponse;
import com.example.racehorse_transport.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/manager/bookings")
@RequiredArgsConstructor
public class ManagerBookingController {

    private final BookingService bookingService;

    // 1. API Manager lấy danh sách đơn chờ tiếp nhận (hoặc lọc theo status)
    @GetMapping("/intake")
    public ResponseEntity<ApiResponse<List<BookingResponse>>> getIntakeBookings(
            @RequestParam(value = "status", required = false, defaultValue = "pending_intake") String status) {
        List<BookingResponse> bookings = bookingService.getBookingsByStatus(status);
        return ResponseEntity.ok(ApiResponse.success(bookings, "Lấy danh sách đơn chờ tiếp nhận thành công"));
    }

    // 2. API Manager xem toàn bộ đơn
    @GetMapping
    public ResponseEntity<ApiResponse<List<BookingResponse>>> getAllBookings() {
        List<BookingResponse> bookings = bookingService.getBookingsByStatus("ALL");
        return ResponseEntity.ok(ApiResponse.success(bookings, "Lấy danh sách toàn bộ đơn hàng thành công"));
    }

    // 3. API Manager phân công kíp xe (1 Driver + 1 Escort cho từng xe)
    @PostMapping("/{id}/assign-crew")
    public ResponseEntity<ApiResponse<BookingResponse>> assignCrew(
            @PathVariable("id") String id,
            @jakarta.validation.Valid @RequestBody com.example.racehorse_transport.dto.manager.AssignCrewRequest request) {
        BookingResponse response = bookingService.assignCrew(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Phân công kíp xe thành công"));
    }

    // 4. API Manager duyệt và phát hành báo giá (hiệu lực 48h)
    @PostMapping("/{id}/send-quote")
    public ResponseEntity<ApiResponse<BookingResponse>> sendQuote(
            @PathVariable("id") String id,
            @jakarta.validation.Valid @RequestBody com.example.racehorse_transport.dto.manager.SendQuoteRequest request) {
        BookingResponse response = bookingService.sendQuote(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Phát hành báo giá thành công (hiệu lực 48 giờ)"));
    }

    // 5. API Manager trả đơn về Specialist hoặc Coordinator
    @PostMapping("/{id}/send-back")
    public ResponseEntity<ApiResponse<BookingResponse>> sendBack(
            @PathVariable("id") String id,
            @jakarta.validation.Valid @RequestBody com.example.racehorse_transport.dto.manager.SendBackRequest request) {
        BookingResponse response = bookingService.sendBack(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Đã trả lại hồ sơ đơn hàng"));
    }

    // 6. API Kích hoạt quét & giải phóng xe/kíp xe quá hạn 48h (Dành cho Quản lý / Batch Job)
    @PostMapping("/maintenance/release-expired")
    public ResponseEntity<ApiResponse<Integer>> releaseExpired() {
        int count = bookingService.releaseExpiredQuotations();
        return ResponseEntity.ok(ApiResponse.success(count, "Đã quét và giải phóng thành công " + count + " đơn hết hạn"));
    }
}
