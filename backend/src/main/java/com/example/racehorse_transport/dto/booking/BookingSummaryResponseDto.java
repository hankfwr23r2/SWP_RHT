package com.example.racehorse_transport.dto.booking;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.BookingType;
import java.time.Instant;
import java.time.LocalDate;

// Một dòng trên danh sách đơn của nhân viên. Số ngựa đang đi do service đếm.
public record BookingSummaryResponseDto(Long id, String code, BookingType bookingType, BookingStatus status,
        String customerName, String customerPhone, int horseCount, Long departureId, String routeName,
        LocalDate departureDate, LocalDate requestedPickupDate, String waybillNumber, Instant createdAt,
        Long specialistId, String specialistName) {

    public static BookingSummaryResponseDto from(Booking booking, int horseCount) {
        var departure = booking.getDeparture();
        return new BookingSummaryResponseDto(booking.getId(), booking.getCode(), booking.getBookingType(), booking.getStatus(),
                booking.getCustomer().getFullName(), booking.getCustomer().getPhone(), horseCount,
                departure == null ? null : departure.getId(),
                departure == null ? null : departure.getRoute().getName(),
                departure == null ? null : departure.getDepartureDate(),
                booking.getRequestedPickupDate(), booking.getWaybillNumber(), booking.getCreatedAt(),
                booking.getSpecialist() == null ? null : booking.getSpecialist().getUserId(),
                booking.getSpecialist() == null ? null : booking.getSpecialist().getFullName());
    }
}
