package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.booking.BookingResponseDto;
import com.example.racehorse_transport.dto.booking.CreateBookingRequestDto;
import com.example.racehorse_transport.dto.booking.CustomerTripTeamResponseDto;
import com.example.racehorse_transport.dto.booking.RejectQuoteRequestDto;
import com.example.racehorse_transport.dto.common.PagedResponseDto;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.service.BookingQueryService;
import com.example.racehorse_transport.service.BookingService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/customer/bookings")
@PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
@RequiredArgsConstructor
public class CustomerBookingController {

    private final BookingService bookingService;
    private final BookingQueryService bookingQueries;
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
    public ResponseEntity<ApiResponseDto<BookingResponseDto>> createBooking(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @Valid @RequestBody CreateBookingRequestDto request) {
        String username = extractUsername(authHeader);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponseDto.error("Vui lòng đăng nhập để đặt chuyến!"));
        }

        BookingResponseDto response = bookingService.createBooking(username, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(response, "Gửi yêu cầu đặt đơn thành công"));
    }

    // 2. API Khách hàng lấy danh sách đơn của mình (hỗ trợ phân trang tùy chọn)
    @GetMapping
    public ResponseEntity<ApiResponseDto<?>> getMyBookings(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "20") Integer size) {
        String username = extractUsername(authHeader);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponseDto.error("Vui lòng đăng nhập để xem đơn hàng!"));
        }

        if (page != null) {
            PagedResponseDto<BookingResponseDto> paged = bookingService.getCustomerBookingsPaged(username, page, size);
            return ResponseEntity.ok(ApiResponseDto.success(paged, "Lấy danh sách đơn hàng thành công"));
        }

        List<BookingResponseDto> bookings = bookingService.getCustomerBookings(username);
        return ResponseEntity.ok(ApiResponseDto.success(bookings, "Lấy danh sách đơn hàng thành công"));
    }

    // 3. API Xem chi tiết 1 đơn hàng
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDto<BookingResponseDto>> getBookingDetail(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @PathVariable String id) {
        String username = extractUsername(authHeader);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponseDto.error("Vui lòng đăng nhập để xem chi tiết đơn!"));
        }

        BookingResponseDto response = bookingService.getBookingById(bookingQueries.resolveBookingId(id), username);
        return ResponseEntity.ok(ApiResponseDto.success(response, "Lấy chi tiết đơn hàng thành công"));
    }

    // 4. API Xe và kíp xe của từng chuyến trong đơn (khách không gọi được /fleet nội bộ)
    @GetMapping("/{id}/team")
    public ResponseEntity<ApiResponseDto<List<CustomerTripTeamResponseDto>>> getTripTeam(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @PathVariable String id) {
        String username = extractUsername(authHeader);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponseDto.error("Vui lòng đăng nhập để xem chi tiết đơn!"));
        }

        List<CustomerTripTeamResponseDto> team = bookingService.getCustomerTripTeam(bookingQueries.resolveBookingId(id), username);
        return ResponseEntity.ok(ApiResponseDto.success(team, "Lấy thông tin xe và kíp xe thành công"));
    }

    // 5. API Khách hàng từ chối báo giá (giải phóng xe và kíp xe)
    @PostMapping("/{id}/reject-quote")
    public ResponseEntity<ApiResponseDto<BookingResponseDto>> rejectQuote(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @PathVariable("id") String id,
            @RequestBody(required = false) RejectQuoteRequestDto request) {
        String username = extractUsername(authHeader);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponseDto.error("Vui lòng đăng nhập để thao tác!"));
        }

        BookingResponseDto response = bookingService.rejectQuote(id, username, request);
        return ResponseEntity.ok(ApiResponseDto.success(response, "Đã từ chối báo giá. Đơn hàng đã hủy và giải phóng xe."));
    }

    // 6. API Khách hàng gửi lại hồ sơ ngựa sau khi được yêu cầu bổ sung
    @PostMapping("/{id}/resubmit")
    public ResponseEntity<ApiResponseDto<BookingResponseDto>> resubmitDocuments(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @PathVariable("id") String id) {
        String username = extractUsername(authHeader);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponseDto.error("Vui lòng đăng nhập để gửi lại hồ sơ!"));
        }

        BookingResponseDto response = bookingService.resubmitDocuments(id, username);
        return ResponseEntity.ok(ApiResponseDto.success(response, "Đã gửi lại hồ sơ cho Chuyên viên kiểm dịch thẩm định"));
    }
}
