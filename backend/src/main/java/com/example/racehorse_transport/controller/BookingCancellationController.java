package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.booking.BookingCancellationResponseDto;
import com.example.racehorse_transport.dto.booking.CancelBookingRequestDto;
import com.example.racehorse_transport.dto.booking.CompanyCancelBookingRequestDto;
import com.example.racehorse_transport.dto.booking.ManagerCancelBookingRequestDto;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.service.BookingCancellationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bookings/{id}")
public class BookingCancellationController {
    private final BookingCancellationService cancellations;

    @GetMapping("/cancellation-preview")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponseDto<BookingCancellationResponseDto> preview(@PathVariable Long id, Authentication actor) {
        return ApiResponseDto.success(cancellations.preview(id, actor.getName()), "Thành công.");
    }

    @GetMapping("/manager-cancellation-preview")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponseDto<BookingCancellationResponseDto> previewForManager(@PathVariable Long id, @RequestParam boolean byCompany) {
        return ApiResponseDto.success(cancellations.previewForManager(id, byCompany), "Thành công.");
    }

    @PostMapping("/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponseDto<BookingCancellationResponseDto> cancel(@PathVariable Long id, Authentication actor,
            @Valid @RequestBody CancelBookingRequestDto request) {
        return ApiResponseDto.success(cancellations.cancelByCustomer(id, actor.getName(), request), "Đã hủy đơn.");
    }

    @PostMapping("/cancel-for-company")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponseDto<BookingCancellationResponseDto> cancelForCompany(@PathVariable Long id, Authentication actor,
            @Valid @RequestBody CompanyCancelBookingRequestDto request) {
        return ApiResponseDto.success(cancellations.cancelForCompany(id, actor.getName(), request), "Đã hủy đơn.");
    }

    @PostMapping("/cancel-for-customer")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponseDto<BookingCancellationResponseDto> cancelForCustomer(@PathVariable Long id, Authentication actor,
            @Valid @RequestBody ManagerCancelBookingRequestDto request) {
        return ApiResponseDto.success(cancellations.cancelForCustomer(id, actor.getName(), request), "Đã hủy đơn.");
    }
}
