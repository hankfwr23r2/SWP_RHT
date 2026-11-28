package com.example.racehorse_transport.dto.booking;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingAddress;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.entity.enums.TransportMode;
import java.time.Instant;
import java.time.LocalDate;

// Một dòng trên danh sách đơn. Số ngựa đang đi do service đếm, nơi giao do service nạp (có thể không có).
public record BookingSummaryResponseDto(Long id, String code, BookingType bookingType, TransportMode transportMode, BookingStatus status,
        String customerName, String customerPhone, int horseCount, Long departureId, String routeName,
        LocalDate departureDate, LocalDate requestedPickupDate, String waybillNumber, Instant createdAt,
        Long specialistId, String specialistName, String destinationProvinceName, String destinationCountryName) {

    public static BookingSummaryResponseDto from(Booking booking, int horseCount, BookingAddress delivery) {
        var departure = booking.getDeparture();
        return new BookingSummaryResponseDto(booking.getId(), booking.getCode(), booking.getBookingType(), booking.getTransportMode(), booking.getStatus(),
                booking.getCustomer().getFullName(), booking.getCustomer().getPhone(), horseCount,
                departure == null ? null : departure.getId(),
                departure == null ? null : departure.getRoute().getName(),
                departure == null ? null : departure.getDepartureDate(),
                booking.getRequestedPickupDate(), booking.getWaybillNumber(), booking.getCreatedAt(),
                booking.getSpecialist() == null ? null : booking.getSpecialist().getUserId(),
                booking.getSpecialist() == null ? null : booking.getSpecialist().getFullName(),
                delivery == null ? null : delivery.getProvince().getName(),
                delivery == null ? null : delivery.getCountry().getName());
    }
}
