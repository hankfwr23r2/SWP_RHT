package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.*;
import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.BookingAssignmentPolicy;
import com.example.racehorse_transport.policy.BookingReviewPolicy;
import com.example.racehorse_transport.repository.*;
import com.example.racehorse_transport.service.BookingAssignmentService;
import com.example.racehorse_transport.service.NotificationService;
import com.example.racehorse_transport.service.CoordinatorWorkloadService;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.example.racehorse_transport.policy.BookingAssignmentPolicy.CLOSED_WORKLOAD;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingAssignmentServiceImpl implements BookingAssignmentService {
    private final AppUserRepository users;
    private final StaffRepository staff;
    private final BookingRepository bookings;
    private final BookingSpecialistHistoryRepository history;
    private final NotificationService notifications;
    private final Clock clock;
    private final CoordinatorWorkloadService coordinatorWorkloads;

    @Override
    public AppUser actor(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản."));
    }

    @Override
    public Staff requireAssigned(Booking booking, String username) {
        return BookingAssignmentPolicy.requireAssigned(booking.getSpecialist(), actor(username));
    }

    @Override
    @Transactional
    public void assignAtIntake(Booking booking, Long specialistId, String username) {
        assign(booking, specialistId, manager(username), null);
    }

    @Override
    @Transactional
    public BookingResponseDto reassign(Long id, BookingReassignmentRequestDto request, String username) {
        AppUser manager = manager(username);
        Booking booking = bookings.findForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn."));
        BookingAssignmentPolicy.requireReassignable(booking.getStatus());
        assign(booking, request.specialistId(), manager, BookingReviewPolicy.requireReason(request.reason()));
        return BookingResponseDto.from(booking);
    }

    private AppUser manager(String username) {
        AppUser actor = actor(username);
        if (!"MANAGER".equals(actor.getRole().getCode())) {
            throw BookingRuleException.forbidden("Chỉ quản lý được phân công chuyên viên.");
        }
        return actor;
    }

    private void assign(Booking booking, Long specialistId, AppUser actor, String reason) {
        Staff specialist = staff.findForUpdate(specialistId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyên viên."));
        BookingAssignmentPolicy.requireAvailable(specialist);
        Staff previous = booking.getSpecialist();
        if (reason != null && previous != null && specialistId.equals(previous.getUserId())) {
            throw BookingRuleException.conflict("Chuyên viên đã phụ trách đơn này.");
        }
        booking.setSpecialist(specialist);
        var change = new BookingSpecialistHistory();
        change.setBooking(booking);
        change.setFromStaff(previous);
        change.setToStaff(specialist);
        change.setChangedBy(actor);
        change.setReason(reason);
        change.setChangedAt(clock.instant());
        history.save(change);
        Long bookingId = booking.getId();
        String link = "/specialist/review/" + bookingId;
        notifications.notify(List.of(specialistId), bookingId, "Bạn được giao phụ trách đơn",
                reason == null ? "Kiểm tra hồ sơ trong chi tiết đơn." : reason, link);
        if (previous != null && !specialistId.equals(previous.getUserId())) {
            notifications.notify(List.of(previous.getUserId()), bookingId, "Đơn đã đổi chuyên viên",
                    reason == null ? "Đơn đã được giao cho chuyên viên khác khi tiếp nhận." : reason, link);
        }
    }

    @Override
    public String reviewLink(Booking booking) {
        return (booking.getSpecialist() == null ? "/manager/orders/" : "/specialist/review/") + booking.getId();
    }

    @Override
    public List<Long> recipients(Booking booking) {
        return booking.getSpecialist() == null
                ? staff.findUserIdsByRoleAndStatus("MANAGER", AppUserStatus.ACTIVE)
                : List.of(booking.getSpecialist().getUserId());
    }

    @Override
    public List<StaffWorkloadResponseDto> workloads(String role, String username) {
        manager(username);
        if ("COORDINATOR".equals(role)) {
            return coordinatorWorkloads.workloads();
        }
        if (!"SPECIALIST".equals(role)) {
            throw new BookingRuleException("role", "Vai trò chưa được hỗ trợ.");
        }
        return staff.findActiveForRole(role).stream().map(person -> StaffWorkloadResponseDto.from(person,
                bookings.countBySpecialist_UserIdAndStatusNotIn(person.getUserId(), CLOSED_WORKLOAD))).toList();
    }

    @Override
    public List<BookingResponseDto> assignedBookings(Long staffId, String username) {
        manager(username);
        return bookings.findBySpecialist_UserIdAndStatusNotInOrderByCreatedAtAscIdAsc(staffId, CLOSED_WORKLOAD)
                .stream().map(BookingResponseDto::from).toList();
    }

    @Override
    @Transactional
    public void requireAvailableForReopening(Booking booking) {
        if (booking.getSpecialist() != null) {
            var specialist = staff.findForUpdate(booking.getSpecialist().getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyên viên."));
            BookingAssignmentPolicy.requireAvailable(specialist);
        }
    }

    @Override
    public void requireNoWork(Staff person) {
        if ("COORDINATOR".equals(person.getUser().getRole().getCode())) {
            coordinatorWorkloads.requireNoWork(person.getUserId());
        }
        if ("SPECIALIST".equals(person.getUser().getRole().getCode())) {
            BookingAssignmentPolicy.requireNoWork(
                    bookings.countBySpecialist_UserIdAndStatusNotIn(person.getUserId(), CLOSED_WORKLOAD));
        }
    }
}
