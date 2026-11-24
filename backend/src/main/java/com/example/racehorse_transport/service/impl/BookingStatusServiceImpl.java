package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.AppUser;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingStatusHistory;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.BookingStatusPolicy;
import com.example.racehorse_transport.repository.AppUserRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.BookingStatusHistoryRepository;
import com.example.racehorse_transport.service.BookingStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Nơi duy nhất đổi booking.status (DB_SCHEMA_59_BANG.md quy tắc #18). Mỗi lần đổi có một dòng booking_status_history.
@Service
@RequiredArgsConstructor
public class BookingStatusServiceImpl implements BookingStatusService {

    private final BookingRepository bookings;
    private final BookingStatusHistoryRepository history;
    private final AppUserRepository users;

    @Override
    @Transactional
    public void transition(Long bookingId, BookingStatus toStatus, String reason, Long actorUserId) {
        Booking booking = bookings.findForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn " + bookingId));
        BookingStatus from = booking.getStatus();
        if (!BookingStatusPolicy.canTransition(from, toStatus)) {
            throw new IllegalStateException("Đơn " + booking.getCode() + " không chuyển được từ " + from + " sang " + toStatus + ".");
        }
        booking.setStatus(toStatus);

        recordHistory(booking, from, reason, actorUserId == null ? null : users.getReferenceById(actorUserId));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordInitialStatus(Booking booking) {
        recordHistory(booking, null, null, booking.getCreatedBy());
    }

    private void recordHistory(Booking booking, BookingStatus from, String reason,
                               AppUser actor) {
        BookingStatusHistory row = new BookingStatusHistory();
        row.setBooking(booking);
        row.setFromStatus(from);
        row.setToStatus(booking.getStatus());
        row.setChangedBy(actor);
        row.setReason(reason == null || reason.isBlank() ? null : reason.trim());
        history.save(row);
    }
}
