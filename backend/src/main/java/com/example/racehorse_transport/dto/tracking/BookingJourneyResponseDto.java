package com.example.racehorse_transport.dto.tracking;

import com.example.racehorse_transport.entity.enums.BookingStatus;
import java.util.List;

// Hành trình mọi con ngựa của một đơn (trang chi tiết đơn của nhân viên và khách)
public record BookingJourneyResponseDto(Long bookingId, String bookingCode, BookingStatus status, List<HorseJourneyResponseDto> horses) {
}
