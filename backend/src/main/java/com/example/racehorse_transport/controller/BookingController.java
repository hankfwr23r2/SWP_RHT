package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.booking.BookingResponse;
import com.example.racehorse_transport.dto.booking.PublicTrackingResponse;
import com.example.racehorse_transport.response.ApiResponse;
import com.example.racehorse_transport.service.BookingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/bookings")
public class BookingController {

    private final BookingService bookingService;

    @Autowired
    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    // Tra cứu đơn công khai theo mã đơn và 4 số cuối số điện thoại (không cần đăng nhập)
    @GetMapping("/public-track")
    public ResponseEntity<ApiResponse<PublicTrackingResponse>> getPublicTracking(
            @RequestParam("code") String code,
            @RequestParam("phoneLast4") String phoneLast4) {
        PublicTrackingResponse response = bookingService.getPublicTracking(code, phoneLast4);
        if (response == null) {
            return ResponseEntity.status(404).body(ApiResponse.error("Không tìm thấy đơn hàng hoặc 4 số cuối điện thoại không khớp."));
        }
        return ResponseEntity.ok(ApiResponse.success(response, "Tra cứu tiến độ đơn hàng thành công"));
    }

    @PreAuthorize("hasAnyRole('DRIVER', 'ESCORT')")
    @GetMapping("/assigned")
    public ResponseEntity<ApiResponse<List<BookingResponse>>> getAssignedBookings(
            Authentication authentication) {
        List<BookingResponse> bookings = bookingService.getAssignedBookings(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(bookings, "Lấy danh sách chuyến được phân công thành công"));
    }
}
