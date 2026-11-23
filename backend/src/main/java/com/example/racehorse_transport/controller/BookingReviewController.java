package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.booking.*;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.service.*;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingReviewController {
    private final BookingReviewService review;
    private final BookingQueryService queries;

    @GetMapping("/review-queue")
    @PreAuthorize("hasAnyRole('SPECIALIST','MANAGER','COORDINATOR')")
    public ApiResponseDto<List<BookingResponseDto>> queue() {
        return ApiResponseDto.success(queries.reviewQueue(), "Thành công.");
    }

    @GetMapping("/{id}/review-detail")
    @PreAuthorize("hasAnyRole('SPECIALIST','MANAGER','COORDINATOR')")
    public ApiResponseDto<BookingDetailResponseDto> detail(@PathVariable Long id) {
        return ApiResponseDto.success(queries.reviewDetail(id), "Thành công.");
    }

    @PostMapping("/{id}/intake")
    @PreAuthorize("hasAnyRole('SPECIALIST','MANAGER')")
    public ApiResponseDto<BookingResponseDto> intake(@PathVariable Long id, Authentication authentication,
            @Valid @RequestBody(required = false) BookingIntakeRequestDto request) {
        return ApiResponseDto.success(review.intake(id, authentication.getName()), "Thành công.");
    }

    @PostMapping("/{id}/return-for-resubmission")
    @PreAuthorize("hasAnyRole('SPECIALIST','MANAGER')")
    public ApiResponseDto<BookingResponseDto> returnToCustomer(@PathVariable Long id, Authentication authentication, @Valid @RequestBody BookingReturnRequestDto request) {
        return ApiResponseDto.success(review.returnToCustomer(id, authentication.getName(), request.reason()), "Thành công.");
    }

    @PostMapping("/{id}/resubmit")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponseDto<BookingResponseDto> resubmit(@PathVariable Long id, Authentication authentication,
            @Valid @RequestBody(required = false) BookingResubmissionRequestDto request) {
        return ApiResponseDto.success(review.resubmit(id, authentication.getName()), "Thành công.");
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('SPECIALIST','MANAGER')")
    public ApiResponseDto<BookingResponseDto> reject(@PathVariable Long id, Authentication authentication, @Valid @RequestBody BookingRejectionRequestDto request) {
        return ApiResponseDto.success(review.reject(id, authentication.getName(), request.reason()), "Thành công.");
    }

    @PostMapping("/{id}/start-quoting")
    @PreAuthorize("hasRole('SPECIALIST')")
    public ApiResponseDto<BookingResponseDto> startQuoting(@PathVariable Long id, Authentication authentication,
            @Valid @RequestBody(required = false) BookingQuotingRequestDto request) {
        return ApiResponseDto.success(review.startQuoting(id, authentication.getName()), "Thành công.");
    }

    @PostMapping("/{id}/change-departure")
    @PreAuthorize("hasRole('COORDINATOR')")
    public ApiResponseDto<BookingResponseDto> changeDeparture(@PathVariable Long id, @Valid @RequestBody BookingDepartureChangeRequestDto request) {
        return ApiResponseDto.success(review.changeDeparture(id, request.departureId()), "Thành công.");
    }
}
