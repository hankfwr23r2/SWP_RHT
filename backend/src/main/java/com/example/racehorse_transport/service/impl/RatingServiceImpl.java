package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.RatingRequestDto;
import com.example.racehorse_transport.dto.booking.RatingResponseDto;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.BookingRatingRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.service.RatingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RatingServiceImpl implements RatingService {
    private final BookingRepository bookings;
    private final BookingRatingRepository ratings;

    @Override
    @Transactional
    public RatingResponseDto rate(Long bookingId, String username, RatingRequestDto request) {
        var booking = bookings.findForUpdate(bookingId)
                .filter(row -> username.equals(row.getCustomer().getUser().getUsername()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn."));
        if (booking.getStatus() != BookingStatus.DELIVERED && booking.getStatus() != BookingStatus.COMPLETED) {
            throw BookingRuleException.conflict("Chỉ đánh giá sau khi đơn đã giao.");
        }
        if (ratings.existsById(bookingId)) {
            throw BookingRuleException.conflict("Đơn đã được đánh giá.");
        }
        if (request.score() == null || request.score() < 1 || request.score() > 5) {
            throw new BookingRuleException("Điểm đánh giá phải từ 1 đến 5.");
        }
        return RatingResponseDto.from(ratings.saveAndFlush(request.toEntity(booking)));
    }
}
