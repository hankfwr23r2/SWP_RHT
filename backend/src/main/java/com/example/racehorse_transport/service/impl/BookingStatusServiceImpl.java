package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.AppUser;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingStatusHistory;
import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.enums.ScheduleChangeCause;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.BookingStatusPolicy;
import com.example.racehorse_transport.repository.AppUserRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.BookingStatusHistoryRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.service.BookingStatusService;
import com.example.racehorse_transport.service.CustomerNoticeService;
import com.example.racehorse_transport.service.NotificationService;
import com.example.racehorse_transport.service.CollectionCodeService;
import java.util.ArrayList;
import java.util.List;
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
    private final NotificationService notifications;
    private final StaffRepository staff;
    private final CustomerNoticeService notices;
    private final CollectionCodeService collectionCodes;

    @Override
    @Transactional
    public void transition(Long bookingId, BookingStatus toStatus, String reason, Long actorUserId) {
        transition(bookingId, toStatus, reason, actorUserId, null, null);
    }

    @Override
    @Transactional
    public void requireScheduleChange(Long bookingId, Departure previousDeparture, ScheduleChangeCause cause,
                                      String reason, Long actorUserId) {
        transition(bookingId, BookingStatus.SCHEDULE_CHANGE_REQUIRED, reason, actorUserId, previousDeparture, cause);
    }

    @Override
    @Transactional
    public void requireScheduleChangeForUser(Long bookingId, Departure previousDeparture, ScheduleChangeCause cause,
                                             String reason, String username) {
        requireScheduleChange(bookingId, previousDeparture, cause, reason, actorId(username));
    }

    private void transition(Long bookingId, BookingStatus toStatus, String reason, Long actorUserId,
                            Departure previousDeparture, ScheduleChangeCause cause) {
        Booking booking = bookings.findForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn " + bookingId));
        BookingStatus from = booking.getStatus();
        if (!BookingStatusPolicy.canTransition(from, toStatus)) {
            throw new IllegalStateException("Đơn " + booking.getCode() + " không chuyển được từ " + from + " sang " + toStatus + ".");
        }
        booking.setStatus(toStatus);
        if (toStatus == BookingStatus.IN_OPERATION) {
            collectionCodes.issue(booking);
        }

        recordHistory(booking, from, reason, actorUserId == null ? null : users.getReferenceById(actorUserId),
                previousDeparture, cause);
        if (toStatus == BookingStatus.COMPLETED) {
            notifications.notify(List.of(booking.getCustomer().getUser().getId()), bookingId, "Đã đóng đơn",
                    "Đơn đã hoàn tất quyết toán.", null);
        } else if (toStatus == BookingStatus.CANCELLED) {
            var recipients = new ArrayList<>(staff.findUserIdsByRoleAndStatus("COORDINATOR", AppUserStatus.ACTIVE));
            recipients.add(booking.getCustomer().getUser().getId());
            notifications.notify(recipients, bookingId, "Đã hủy đơn",
                    reason == null || reason.isBlank() ? "Đơn " + booking.getCode() + " đã hủy."
                            : "Đơn " + booking.getCode() + " đã hủy. Lý do: " + reason.strip(), null);
        }
    }

    @Override
    @Transactional
    public void transitionForUser(Long bookingId, BookingStatus toStatus, String reason, String username) {
        transition(bookingId, toStatus, reason, actorId(username));
    }

    private Long actorId(String username) {
        return users.findByUsername(username).orElseThrow(
                () -> new ResourceNotFoundException("Không tìm thấy người thao tác.")).getId();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordInitialStatus(Booking booking) {
        recordHistory(booking, null, null, booking.getCreatedBy(), null, null);
        notices.bookingSubmitted(booking);
    }

    private void recordHistory(Booking booking, BookingStatus from, String reason,
                               AppUser actor, Departure previousDeparture, ScheduleChangeCause cause) {
        BookingStatusHistory row = new BookingStatusHistory();
        row.setBooking(booking);
        row.setFromStatus(from);
        row.setToStatus(booking.getStatus());
        row.setChangedBy(actor);
        row.setReason(reason == null || reason.isBlank() ? null : reason.trim());
        row.setPreviousDeparture(previousDeparture);
        row.setCauseType(cause);
        history.save(row);
    }
}
