package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto;
import com.example.racehorse_transport.dto.booking.BookingDetailResponseDto;
import com.example.racehorse_transport.dto.booking.BookingRescheduleRequestDto;
import com.example.racehorse_transport.dto.booking.BookingResponseDto;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.service.BookingQueryService;
import com.example.racehorse_transport.service.BookingRescheduleService;
import com.example.racehorse_transport.service.BookingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/bookings")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookings;
    private final BookingQueryService queries;
    private final BookingRescheduleService reschedules;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponseDto<BookingResponseDto> submit(@Valid @RequestBody BookingCreateRequestDto request,
                                                    Authentication authentication, HttpServletRequest httpRequest) {
        return ApiResponseDto.success(bookings.submit(authentication.getName(), httpRequest.getRemoteAddr(), request),
                "Đã gửi đơn.");
    }

    @GetMapping
    public ApiResponseDto<List<BookingResponseDto>> list(Authentication authentication) {
        return ApiResponseDto.success(queries.list(authentication.getName()), "Danh sách đơn.");
    }

    @GetMapping("/{id}")
    public ApiResponseDto<BookingDetailResponseDto> get(@PathVariable Long id, Authentication authentication) {
        return ApiResponseDto.success(queries.get(authentication.getName(), id), "Chi tiết đơn.");
    }

    // Đơn chờ chọn lại lịch, chưa cọc
    @PostMapping("/{id}/reschedule")
    public ApiResponseDto<BookingResponseDto> reschedule(@PathVariable Long id, @Valid @RequestBody BookingRescheduleRequestDto request,
                                                         Authentication authentication) {
        return ApiResponseDto.success(reschedules.reschedule(id, authentication.getName(), request), "Đã đổi chuyến.");
    }
}
