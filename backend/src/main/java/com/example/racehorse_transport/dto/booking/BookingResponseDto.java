package com.example.racehorse_transport.dto.booking;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.BookingType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record BookingResponseDto(Long id, String code, BookingType bookingType, BookingStatus status, Long departureId,
        LocalDate requestedPickupDate, String waybillNumber, BigDecimal emergencySpendingLimit, Instant createdAt,
        Long specialistId, String specialistName) {

    public static BookingResponseDto from(Booking booking) {
        Long departureId = booking.getDeparture() == null ? null : booking.getDeparture().getId();
        return new BookingResponseDto(booking.getId(), booking.getCode(), booking.getBookingType(), booking.getStatus(),
                departureId, booking.getRequestedPickupDate(), booking.getWaybillNumber(),
                booking.getEmergencySpendingLimit(), booking.getCreatedAt(),
                booking.getSpecialist() == null ? null : booking.getSpecialist().getUserId(),
                booking.getSpecialist() == null ? null : booking.getSpecialist().getFullName());
    }
}
