package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.booking.BookingCancellationResponseDto;
import com.example.racehorse_transport.dto.booking.BookingDelayResponseDto;
import com.example.racehorse_transport.dto.booking.BookingRescheduleRequestDto;
import com.example.racehorse_transport.dto.booking.BookingResponseDto;
import com.example.racehorse_transport.dto.booking.CancelBookingRequestDto;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.service.BookingDelayService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bookings/{bookingId}/delay")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class BookingDelayController {
    private final BookingDelayService delays;

    @GetMapping
    public ApiResponseDto<List<BookingDelayResponseDto>> bookingDelay(@PathVariable Long bookingId, Authentication actor) {
        return ApiResponseDto.success(delays.view(bookingId, actor.getName()), "Thông tin chuyến chậm.");
    }

    @PostMapping("/reschedule")
    public ApiResponseDto<BookingResponseDto> rescheduleAfterDelay(@PathVariable Long bookingId, Authentication actor,
                                                              @Valid @RequestBody BookingRescheduleRequestDto request) {
        return ApiResponseDto.success(delays.reschedule(bookingId, actor.getName(), request), "Đã đổi chuyến.");
    }

    @GetMapping("/refund-preview")
    public ApiResponseDto<BookingCancellationResponseDto> previewDelayRefund(@PathVariable Long bookingId, Authentication actor) {
        return ApiResponseDto.success(delays.refundPreview(bookingId, actor.getName()), "Thông tin hoàn tiền.");
    }

    @PostMapping("/refund")
    public ApiResponseDto<BookingCancellationResponseDto> refundAfterDelay(@PathVariable Long bookingId, Authentication actor,
                                                                     @Valid @RequestBody CancelBookingRequestDto request) {
        return ApiResponseDto.success(delays.refund(bookingId, actor.getName(), request), "Đã gửi yêu cầu hoàn tiền.");
    }
}
