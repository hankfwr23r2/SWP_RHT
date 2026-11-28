package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.booking.*;
import com.example.racehorse_transport.entity.AppUser;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Staff;
import java.util.List;

public interface BookingAssignmentService {
    AppUser actor(String username);
    Staff requireAssigned(Booking booking, String username);
    void assignAtIntake(Booking booking, Long specialistId, String username);
    BookingResponseDto reassign(Long id, BookingReassignmentRequestDto request, String username);
    String reviewLink(Booking booking);
    List<Long> recipients(Booking booking);
    List<StaffWorkloadResponseDto> workloads(String role, String username);
    List<BookingResponseDto> assignedBookings(Long staffId, String username);
    void requireAvailableForReopening(Booking booking);
    void requireNoWork(Staff staff);
}
