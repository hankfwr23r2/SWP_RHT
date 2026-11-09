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
@RequestMapping("/customer/bookings/{id}/schedule-change")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class BookingScheduleController {
    private final BookingScheduleService schedules;
    private final BookingService bookings;

    @PostMapping
    public ApiResponse<BookingResponse> reschedule(@PathVariable Integer id, Authentication auth,
            @Valid @RequestBody ScheduleChangeRequest request) {
        schedules.reschedule(id, auth.getName(), request.departureDate());
        return ApiResponse.success(bookings.getBookingById(id, auth.getName()), "Đã gửi lịch mới. Chờ công ty kiểm tra và xác nhận.");
    }

    @PostMapping("/cancel")
    public ApiResponse<BookingResponse> cancel(@PathVariable Integer id, Authentication auth,
            @Valid @RequestBody ScheduleCancelRequest request) {
        schedules.cancel(id, auth.getName(), request.reason());
        return ApiResponse.success(bookings.getBookingById(id, auth.getName()), "Đã hủy yêu cầu chưa thanh toán.");
    }

    @PostMapping("/support")
    public ApiResponse<BookingResponse> support(@PathVariable Integer id, Authentication auth) {
        schedules.requestSupport(id, auth.getName());
        return ApiResponse.success(bookings.getBookingById(id, auth.getName()), "Đã gửi yêu cầu quản lý liên hệ hỗ trợ.");
    }
}
