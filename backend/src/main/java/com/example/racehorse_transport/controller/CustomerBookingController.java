package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.booking.BookingResponse;
import com.example.racehorse_transport.dto.booking.CreateBookingRequest;
import com.example.racehorse_transport.response.ApiResponse;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/customer/bookings")
@RequiredArgsConstructor
public class CustomerBookingController {

    private final BookingService bookingService;
    private final JwtUtils jwtUtils;

    private String extractUsername(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtUtils.validateToken(token)) {
                return jwtUtils.getUsernameFromToken(token);
            }
        }
        return null;
    }

    // 1. API Khách hàng tạo yêu cầu đặt chuyến
    @PostMapping
    public ResponseEntity<ApiResponse<BookingResponse>> createBooking(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @Valid @RequestBody CreateBookingRequest request) {
        String username = extractUsername(authHeader);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Vui lòng đăng nhập để đặt chuyến!"));
        }

        BookingResponse response = bookingService.createBooking(username, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Gửi yêu cầu đặt đơn thành công"));
    }

    // 2. API Khách hàng lấy danh sách đơn của mình
    @GetMapping
    public ResponseEntity<ApiResponse<List<BookingResponse>>> getMyBookings(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        String username = extractUsername(authHeader);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Vui lòng đăng nhập để xem đơn hàng!"));
        }

        List<BookingResponse> bookings = bookingService.getCustomerBookings(username);
        return ResponseEntity.ok(ApiResponse.success(bookings, "Lấy danh sách đơn hàng thành công"));
    }

    // 3. API Xem chi tiết 1 đơn hàng
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BookingResponse>> getBookingDetail(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @PathVariable Integer id) {
        String username = extractUsername(authHeader);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Vui lòng đăng nhập để xem chi tiết đơn!"));
        }

        BookingResponse response = bookingService.getBookingById(id, username);
        return ResponseEntity.ok(ApiResponse.success(response, "Lấy chi tiết đơn hàng thành công"));
    }

    // 4. API Khách hàng thanh toán đặt cọc 30% để nhận Vận đơn (Waybill)
    @PostMapping("/{id}/pay-deposit")
    public ResponseEntity<ApiResponse<BookingResponse>> payDeposit(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @PathVariable("id") String id,
            @RequestBody(required = false) com.example.racehorse_transport.dto.booking.PayDepositRequest request) {
        String username = extractUsername(authHeader);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Vui lòng đăng nhập để thanh toán cọc!"));
        }

        BookingResponse response = bookingService.payDeposit(id, username, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Thanh toán cọc 30% thành công! Vận đơn đã được cấp."));
    }

    // 5. API Khách hàng từ chối báo giá (giải phóng xe và kíp xe)
    @PostMapping("/{id}/reject-quote")
    public ResponseEntity<ApiResponse<BookingResponse>> rejectQuote(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @PathVariable("id") String id,
            @RequestBody(required = false) com.example.racehorse_transport.dto.booking.RejectQuoteRequest request) {
        String username = extractUsername(authHeader);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Vui lòng đăng nhập để thao tác!"));
        }

        BookingResponse response = bookingService.rejectQuote(id, username, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Đã từ chối báo giá. Đơn hàng đã hủy và giải phóng xe."));
    }
}
