package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.booking.BookingResponseDto;
import com.example.racehorse_transport.dto.field.TripUpdateRequestDto;
import com.example.racehorse_transport.dto.incident.IncidentResponseDto;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.service.BookingQueryService;
import com.example.racehorse_transport.service.BookingService;
import com.example.racehorse_transport.service.IncidentService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/field/bookings")
@PreAuthorize("hasAnyRole('DRIVER', 'ESCORT', 'COORDINATOR', 'MANAGER', 'ADMIN')")
@RequiredArgsConstructor
public class FieldBookingController {

    private final BookingService bookingService;
    private final BookingQueryService bookingQueries;
    private final IncidentService incidentService;

    // 1. Kíp xe (Tài xế / Hộ tống) lấy danh sách các đơn vận chuyển (tất cả — dành cho MANAGER/ADMIN)
    @GetMapping
    public ResponseEntity<ApiResponseDto<List<BookingResponseDto>>> getFieldBookings(
            @RequestParam(value = "status", required = false, defaultValue = "ALL") String status) {
        List<BookingResponseDto> bookings = bookingService.getBookingsByStatus(status);
        return ResponseEntity.ok(ApiResponseDto.success(bookings, "Lấy danh sách đơn hàng cho kíp xe thành công"));
    }

    // 1b. Driver/Escort lấy CHÍNH XÁC chuyến được phân công cho mình (filter server-side theo JWT)
    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('DRIVER', 'ESCORT', 'ADMIN')")
    public ResponseEntity<ApiResponseDto<List<BookingResponseDto>>> getMyFieldBookings(Authentication auth) {
        List<BookingResponseDto> bookings = bookingService.getMyFieldBookings(auth.getName(), roleOf(auth));
        return ResponseEntity.ok(ApiResponseDto.success(bookings, "Lấy danh sách chuyến của bạn thành công"));
    }

    // 1c. Sự cố của mọi chuyến được phân công cho mình, một lần gọi thay cho từng đơn
    @GetMapping("/mine/incidents")
    @PreAuthorize("hasAnyRole('DRIVER', 'ESCORT', 'ADMIN')")
    public ResponseEntity<ApiResponseDto<List<IncidentResponseDto>>> getMyFieldIncidents(Authentication auth) {
        List<Integer> bookingIds = bookingService.getMyFieldBookings(auth.getName(), roleOf(auth)).stream()
                .map(b -> bookingQueries.resolveBookingId(b.getId()))
                .toList();
        return ResponseEntity.ok(ApiResponseDto.success(incidentService.getIncidentsForBookings(bookingIds), "Lấy sự cố các chuyến của bạn thành công"));
    }

    private String roleOf(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .findFirst().orElse("DRIVER");
    }

    // 2. Kíp xe xem chi tiết một đơn
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDto<BookingResponseDto>> getBookingDetail(@PathVariable("id") String id) {
        BookingResponseDto response = bookingService.getBookingById(bookingQueries.resolveBookingId(id), null);
        return ResponseEntity.ok(ApiResponseDto.success(response, "Lấy chi tiết đơn hàng thành công"));
    }

    // 3. Kíp xe cập nhật tiến độ chuyến xe (check-in, quét chip, thu hồ sơ, xuất phát, an sinh, hoàn tất)
    @PostMapping("/{id}/trip-update")
    public ResponseEntity<ApiResponseDto<BookingResponseDto>> updateTripProgress(
            @PathVariable("id") String id,
            @RequestBody TripUpdateRequestDto request,
            Authentication auth) {
        BookingResponseDto response = bookingService.updateTripProgress(id, request, auth.getName(), roleOf(auth));
        return ResponseEntity.ok(ApiResponseDto.success(response, "Cập nhật tiến độ chuyến xe thành công"));
    }
}
