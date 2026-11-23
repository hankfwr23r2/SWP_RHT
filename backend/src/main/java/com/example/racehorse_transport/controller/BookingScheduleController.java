package com.example.racehorse_transport.controller;


import com.example.racehorse_transport.dto.booking.BookingResponseDto;
import com.example.racehorse_transport.dto.booking.ScheduleCancelRequestDto;
import com.example.racehorse_transport.dto.booking.ScheduleChangeRequestDto;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.service.BookingQueryService;
import com.example.racehorse_transport.service.BookingScheduleService;
import com.example.racehorse_transport.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/customer/bookings/{code}/schedule-change")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class BookingScheduleController {
    private final BookingScheduleService schedules;
    private final BookingService bookings;
    private final BookingQueryService bookingQueries;

    @PostMapping
    public ApiResponseDto<BookingResponseDto> reschedule(@PathVariable String code, Authentication auth,
            @Valid @RequestBody ScheduleChangeRequestDto request) {
        Integer id = bookingQueries.resolveBookingId(code);
        schedules.reschedule(id, auth.getName(), request.departureDate());
        return ApiResponseDto.success(bookings.getBookingById(id, auth.getName()), "Đã gửi lịch mới. Chờ công ty kiểm tra và xác nhận.");
    }

    @PostMapping("/cancel")
    public ApiResponseDto<BookingResponseDto> cancel(@PathVariable String code, Authentication auth,
            @Valid @RequestBody ScheduleCancelRequestDto request) {
        Integer id = bookingQueries.resolveBookingId(code);
        schedules.cancel(id, auth.getName(), request.reason());
        return ApiResponseDto.success(bookings.getBookingById(id, auth.getName()), "Đã hủy yêu cầu chưa thanh toán.");
    }

    @PostMapping("/support")
    public ApiResponseDto<BookingResponseDto> support(@PathVariable String code, Authentication auth) {
        Integer id = bookingQueries.resolveBookingId(code);
        schedules.requestSupport(id, auth.getName());
        return ApiResponseDto.success(bookings.getBookingById(id, auth.getName()), "Đã gửi yêu cầu quản lý liên hệ hỗ trợ.");
    }
}
