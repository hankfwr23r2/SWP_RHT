package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.booking.*;
import com.example.racehorse_transport.response.ApiResponse;
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
    private final com.example.racehorse_transport.service.BookingCodeResolver bookingCodes;

    @PostMapping
    public ApiResponse<BookingResponse> reschedule(@PathVariable String code, Authentication auth,
            @Valid @RequestBody ScheduleChangeRequest request) {
        Integer id = bookingCodes.idOf(code);
        schedules.reschedule(id, auth.getName(), request.departureDate());
        return ApiResponse.success(bookings.getBookingById(id, auth.getName()), "Đã gửi lịch mới. Chờ công ty kiểm tra và xác nhận.");
    }

    @PostMapping("/cancel")
    public ApiResponse<BookingResponse> cancel(@PathVariable String code, Authentication auth,
            @Valid @RequestBody ScheduleCancelRequest request) {
        Integer id = bookingCodes.idOf(code);
        schedules.cancel(id, auth.getName(), request.reason());
        return ApiResponse.success(bookings.getBookingById(id, auth.getName()), "Đã hủy yêu cầu chưa thanh toán.");
    }

    @PostMapping("/support")
    public ApiResponse<BookingResponse> support(@PathVariable String code, Authentication auth) {
        Integer id = bookingCodes.idOf(code);
        schedules.requestSupport(id, auth.getName());
        return ApiResponse.success(bookings.getBookingById(id, auth.getName()), "Đã gửi yêu cầu quản lý liên hệ hỗ trợ.");
    }
}
