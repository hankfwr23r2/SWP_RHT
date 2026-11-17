package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.booking.BookingResponse;
import com.example.racehorse_transport.dto.settlement.RatingDto;
import com.example.racehorse_transport.response.ApiResponse;
import com.example.racehorse_transport.service.SettlementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/customer/bookings")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class CustomerRatingController {
    private final SettlementService settlementService;

    @PostMapping("/{id}/rating")
    public ResponseEntity<ApiResponse<BookingResponse>> submitRating(
            @PathVariable String id, @Valid @RequestBody RatingDto request, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                settlementService.submitRating(id, request, authentication.getName()), "Đã gửi đánh giá chuyến đi"));
    }
}
