package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.tracking.BookingJourneyResponseDto;
import com.example.racehorse_transport.dto.tracking.PublicTrackingRequestDto;
import com.example.racehorse_transport.dto.tracking.PublicTrackingResponseDto;
import com.example.racehorse_transport.service.TrackingService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// Theo dõi hành trình từng con ngựa (B6)
@RestController
@RequiredArgsConstructor
public class TrackingController {

    private final TrackingService tracking;

    // Nhân viên xem mọi đơn, khách chỉ xem đơn của mình (đơn người khác trả 404)
    @GetMapping("/bookings/{id}/journey")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'COORDINATOR', 'MANAGER', 'SPECIALIST')")
    public ApiResponseDto<BookingJourneyResponseDto> journey(@PathVariable Long id, Authentication authentication) {
        boolean customer = authentication.getAuthorities().stream().anyMatch(a -> "ROLE_CUSTOMER".equals(a.getAuthority()));
        return ApiResponseDto.success(customer ? tracking.customerBookingJourney(id, authentication.getName()) : tracking.bookingJourney(id),
                "Hành trình của đơn");
    }

    // Không cần đăng nhập (/public/**). Chặn dò số điện thoại bằng PublicTrackingGuard
    @PostMapping("/public/tracking")
    public ApiResponseDto<List<PublicTrackingResponseDto>> publicTrack(@Valid @RequestBody PublicTrackingRequestDto request) {
        return ApiResponseDto.success(tracking.publicTrack(request), "Kết quả tra cứu");
    }
}
