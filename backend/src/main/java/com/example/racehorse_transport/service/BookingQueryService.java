package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.booking.BookingResponseDto;
import com.example.racehorse_transport.dto.booking.CustomerTripTeamResponseDto;
import com.example.racehorse_transport.dto.booking.PublicTrackingResponseDto;
import com.example.racehorse_transport.dto.common.PagedResponseDto;
import com.example.racehorse_transport.entity.Booking;
import java.util.List;

public interface BookingQueryService {

    List<BookingResponseDto> getCustomerBookings(String customerUsername);

    PagedResponseDto<BookingResponseDto> getCustomerBookingsPaged(String customerUsername, int page, int size);

    BookingResponseDto getBookingById(Integer id, String customerUsername);

    List<CustomerTripTeamResponseDto> getCustomerTripTeam(Integer id, String customerUsername);

    List<BookingResponseDto> getBookingsByStatus(String status);

    PagedResponseDto<BookingResponseDto> getBookingsPaged(String status, int page, int size);

    List<BookingResponseDto> getMyFieldBookings(String username, String role);

    List<BookingResponseDto> getAssignedBookings(String username);

    PublicTrackingResponseDto getPublicTracking(String code, String phoneLast4);

    BookingResponseDto mapToResponse(Booking b);

    Integer resolveBookingId(String code);
}
