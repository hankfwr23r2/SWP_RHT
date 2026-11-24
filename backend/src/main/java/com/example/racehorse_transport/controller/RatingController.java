package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.booking.RatingRequestDto;
import com.example.racehorse_transport.dto.booking.RatingResponseDto;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.service.RatingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bookings/{bookingId}/ratings")
public class RatingController {
    private final RatingService ratings;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponseDto<RatingResponseDto> rate(@PathVariable Long bookingId,
            @Valid @RequestBody RatingRequestDto request, Authentication actor) {
        return ApiResponseDto.success(ratings.rate(bookingId, actor.getName(), request), "Đã lưu đánh giá.");
    }
}
