package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.booking.BookingCancellationResponseDto;
import com.example.racehorse_transport.dto.booking.CancelBookingRequestDto;
import com.example.racehorse_transport.dto.booking.CompanyCancelBookingRequestDto;
import com.example.racehorse_transport.dto.booking.ManagerCancelBookingRequestDto;
import com.example.racehorse_transport.entity.Booking;

public interface BookingCancellationService {
    BookingCancellationResponseDto preview(Long bookingId, String username);

    // byCompany: công ty hủy hay quản lý hủy do khách
    BookingCancellationResponseDto previewForManager(Long bookingId, boolean byCompany);

    BookingCancellationResponseDto cancelByCustomer(Long bookingId, String username, CancelBookingRequestDto request);

    BookingCancellationResponseDto cancelForCompany(Long bookingId, String username, CompanyCancelBookingRequestDto request);

    BookingCancellationResponseDto cancelForCustomer(Long bookingId, String username, ManagerCancelBookingRequestDto request);

    void cancelUnpaidAtPickup(Long bookingId, Long actorUserId);

    void cancelUnansweredBalanceHold(Long bookingId);

    boolean canReleaseUnpaidAtPickup(Booking booking);

    void releaseUnpaidAtPickup(Long bookingId, Long actorUserId);

    void cancelAfterPickupMiss(Long bookingId, boolean customerLiable, Long actorUserId);

    BookingCancellationResponseDto previewAfterPickupMiss(Booking booking, boolean customerLiable);

    void rebookDirectAfterPickupMiss(Long bookingId, java.time.LocalDate pickupDate, Long actorUserId);

    boolean isDirect(com.example.racehorse_transport.entity.Booking booking);

    void releaseAfterPickupMiss(Long bookingId, boolean customerLiable, Long actorUserId);

    void releaseUnpaidAtHub(Long bookingId);
}
