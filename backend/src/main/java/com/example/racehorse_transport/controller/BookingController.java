package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.booking.BookingResponseDto;
import com.example.racehorse_transport.dto.booking.PublicTrackingResponseDto;
import com.example.racehorse_transport.dto.booking.PublicTrackingLookupRequestDto;
import com.example.racehorse_transport.dto.booking.PublicTrackingLookupResponseDto;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.ArrayList;

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
    public ResponseEntity<ApiResponseDto<PublicTrackingResponseDto>> getPublicTracking(
            @RequestParam("code") String code,
            @RequestParam("phoneLast4") String phoneLast4) {
        PublicTrackingResponseDto response = bookingService.getPublicTracking(code, phoneLast4);
        if (response == null) {
            return ResponseEntity.status(404).body(ApiResponseDto.error("Không tìm thấy đơn hàng hoặc 4 số cuối điện thoại không khớp."));
        }
        return ResponseEntity.ok(ApiResponseDto.success(response, "Tra cứu tiến độ đơn hàng thành công"));
    }

    @PostMapping("/public-track/batch")
    public ResponseEntity<ApiResponseDto<List<PublicTrackingLookupResponseDto>>> getPublicTrackingBatch(
            @Valid @RequestBody PublicTrackingLookupRequestDto request) {
        List<PublicTrackingLookupResponseDto> results = new ArrayList<>();
        for (String code : request.codes().stream().distinct().toList()) {
            results.add(new PublicTrackingLookupResponseDto(
                    code,
                    bookingService.getPublicTracking(code, request.phoneLast4())));
        }
        return ResponseEntity.ok(ApiResponseDto.success(results, "Tra cứu tiến độ đơn hàng thành công"));
    }

    @PreAuthorize("hasAnyRole('DRIVER', 'ESCORT')")
    @GetMapping("/assigned")
    public ResponseEntity<ApiResponseDto<List<BookingResponseDto>>> getAssignedBookings(
            Authentication authentication) {
        List<BookingResponseDto> bookings = bookingService.getAssignedBookings(authentication.getName());
        return ResponseEntity.ok(ApiResponseDto.success(bookings, "Lấy danh sách chuyến được phân công thành công"));
    }
}
