package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.service.ExtraChargeService;
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
import com.example.racehorse_transport.service.HorseLegService;
import org.springframework.beans.factory.ObjectProvider;
import java.time.Clock;
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
    private final ExtraChargeService extraCharges;
    private final ObjectProvider<HorseLegService> horseLegs;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public void requireSubmissionAllowed(Long customerId) {
        extraCharges.requireNoOverdue(customerId);
    }

    @Override
    @Transactional
    public void clearPlannedTransport(Long bookingId) {
        horseLegs.getObject().cancelPlannedLegsOfBooking(bookingId);
    }

    @Override
    @Transactional
    public void transition(Long bookingId, BookingStatus toStatus, String reason, Long actorUserId) {
        transition(bookingId, toStatus, reason, actorUserId, null, null);
    }

    @Override
    @Transactional
    public void expireQuote(Long bookingId, Departure previousDeparture) {
        transition(bookingId, BookingStatus.QUOTE_EXPIRED, "Báo giá đã hết hạn.", null, previousDeparture, null);
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

    @Override
    @Transactional
    public void cancelAfterUnclaimedReturns(Long bookingId, Long actorUserId) {
        transition(bookingId, BookingStatus.CANCELLED, "Mọi con ngựa đã được trả sau thông báo nhận lại.",
                actorUserId, null, null, true);
    }

    private void transition(Long bookingId, BookingStatus toStatus, String reason, Long actorUserId,
                            Departure previousDeparture, ScheduleChangeCause cause) {
        transition(bookingId, toStatus, reason, actorUserId, previousDeparture, cause, false);
    }

    private void transition(Long bookingId, BookingStatus toStatus, String reason, Long actorUserId,
                            Departure previousDeparture, ScheduleChangeCause cause, boolean unclaimedReturns) {
        Booking booking = bookings.findForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn " + bookingId));
        BookingStatus from = booking.getStatus();
        boolean allowed = unclaimedReturns
                ? BookingStatusPolicy.canCancelAfterUnclaimedReturns(from)
                : BookingStatusPolicy.canTransition(from, toStatus);
        if (!allowed) {
            throw new IllegalStateException("Đơn " + booking.getCode() + " không chuyển được từ " + from + " sang " + toStatus + ".");
        }
        booking.setStatus(toStatus);
        if (toStatus == BookingStatus.IN_OPERATION) {
            collectionCodes.issue(booking);
        }
        if (toStatus == BookingStatus.CANCELLED || toStatus == BookingStatus.SCHEDULE_CHANGE_REQUIRED
                || toStatus == BookingStatus.REJECTED || toStatus == BookingStatus.QUOTE_EXPIRED) {
            clearPlannedTransport(bookingId);
        }

        recordHistory(booking, from, reason, actorUserId == null ? null : users.getReferenceById(actorUserId),
                previousDeparture, cause);
        notices.statusChanged(booking, from, reason);
        if (toStatus == BookingStatus.CONFIRMED && from == BookingStatus.AWAITING_DEPOSIT) {
            notifications.notifyManagers(bookingId, "Khách đã đặt cọc", "Đơn " + booking.getCode() + " đã xác nhận.",
                    "/manager/orders/" + bookingId);
            var coordinator = booking.getDeparture() == null ? null : booking.getDeparture().getCoordinator();
            var recipients = coordinator == null ? staff.findUserIdsByRoleAndStatus("COORDINATOR", AppUserStatus.ACTIVE)
                    : List.of(coordinator.getUserId());
            if (!recipients.isEmpty()) {
                notifications.notify(recipients, bookingId, "Đơn đã xác nhận chờ xếp xe", booking.getCode(),
                        "/coordinator/dispatch?bookingId=" + bookingId);
            }
        } else if (from == BookingStatus.SCHEDULE_CHANGE_REQUIRED
                && (toStatus == BookingStatus.CONFIRMED || toStatus == BookingStatus.UNDER_REVIEW)) {
            notifications.notifyManagers(bookingId, "Khách đã chọn lịch mới", booking.getCode(), "/manager/orders/" + bookingId);
        } else if (toStatus == BookingStatus.QUOTING) {
            var managers = staff.findUserIdsByRoleAndStatus("MANAGER", AppUserStatus.ACTIVE);
            if (!managers.isEmpty()) {
                boolean rejected = from == BookingStatus.AWAITING_DEPOSIT;
                notifications.notify(managers, bookingId, rejected ? "Khách từ chối báo giá" : "Đơn chờ lập báo giá",
                        "Đơn " + booking.getCode() + (rejected ? " cần báo giá lại. Lý do: " + reason
                                : " đã chuyển sang Đang báo giá."), "/manager/orders/" + bookingId);
            }
        } else if (toStatus == BookingStatus.QUOTE_EXPIRED) {
            notifications.notifyManagers(bookingId, "Báo giá hết hạn, xem xét mở lại", "Báo giá của đơn " + booking.getCode() + " đã hết hạn và đã nhả chỗ.",
                    "/manager/orders/" + bookingId);
            notifications.notify(List.of(booking.getCustomer().getUser().getId()), bookingId, "Báo giá hết hạn",
                    "Báo giá của đơn " + booking.getCode() + " đã hết hạn.", "/orders/" + bookingId);
        } else if (toStatus == BookingStatus.DELIVERED) {
            var managers = staff.findUserIdsByRoleAndStatus("MANAGER", AppUserStatus.ACTIVE);
            if (!managers.isEmpty()) {
                notifications.notify(managers, bookingId, "Đơn đã giao",
                        "Đơn " + booking.getCode() + " đã giao và đang chờ quyết toán.", "/manager/orders/" + bookingId);
            }
        } else if (toStatus == BookingStatus.COMPLETED) {
            notifications.notify(List.of(booking.getCustomer().getUser().getId()), bookingId, "Đã đóng đơn",
                    "Đơn đã hoàn tất quyết toán.", null);
        } else if (toStatus == BookingStatus.CANCELLED) {
            if (booking.getCustomer().getUser().getId().equals(actorUserId)) {
                String title = booking.getWaybillNumber() == null ? "Khách hủy đơn trước cọc" : "Khách hủy đơn đã cọc";
                notifications.notifyManagers(bookingId, title, booking.getCode(), "/manager/orders/" + bookingId);
            }
            String message = reason == null || reason.isBlank() ? "Đơn " + booking.getCode() + " đã hủy."
                    : "Đơn " + booking.getCode() + " đã hủy. Lý do: " + reason.strip();
            var departure = previousDeparture == null ? booking.getDeparture() : previousDeparture;
            var coordinator = departure == null ? null : departure.getCoordinator();
            var recipients = coordinator == null ? staff.findUserIdsByRoleAndStatus("COORDINATOR", AppUserStatus.ACTIVE)
                    : List.of(coordinator.getUserId());
            if (!recipients.isEmpty()) {
                notifications.notify(recipients, bookingId, "Đã hủy đơn", message,
                        "/coordinator/dispatch?bookingId=" + bookingId);
            }
            notifications.notify(List.of(booking.getCustomer().getUser().getId()), bookingId, "Đã hủy đơn", message,
                    "/orders/" + bookingId);
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
        row.setChangedAt(clock.instant());
        history.save(row);
    }
}
