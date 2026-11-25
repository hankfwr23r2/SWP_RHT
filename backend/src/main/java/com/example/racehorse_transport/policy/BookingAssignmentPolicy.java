package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.AppUser;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.EmploymentStatus;
import com.example.racehorse_transport.exception.BookingRuleException;
import java.util.Set;
import static com.example.racehorse_transport.entity.enums.BookingStatus.*;

public final class BookingAssignmentPolicy {
    public static final Set<BookingStatus> CLOSED_WORKLOAD = Set.of(COMPLETED, CANCELLED, REJECTED, QUOTE_EXPIRED);

    private BookingAssignmentPolicy() {}

    public static Staff requireAssigned(Staff specialist, AppUser actor) {
        if (!"SPECIALIST".equals(actor.getRole().getCode())) {
            throw BookingRuleException.forbidden("Đơn chưa được giao cho bạn.");
        }
        if (specialist == null) {
            throw BookingRuleException.conflict("Đơn chưa có chuyên viên phụ trách.");
        }
        if (!specialist.getUserId().equals(actor.getId())) {
            throw BookingRuleException.forbidden("Đơn chưa được giao cho bạn.");
        }
        return specialist;
    }

    public static void requireAvailable(Staff specialist) {
        if (specialist.getEmploymentStatus() != EmploymentStatus.ACTIVE
                || specialist.getUser().getStatus() != AppUserStatus.ACTIVE
                || !"SPECIALIST".equals(specialist.getUser().getRole().getCode())) {
            throw new BookingRuleException("specialistId", "Chuyên viên phải đang làm việc và có tài khoản hoạt động.");
        }
    }

    public static void requireReassignable(BookingStatus status) {
        if (status == COMPLETED || status == CANCELLED || status == REJECTED) {
            throw BookingRuleException.conflict("Không thể đổi chuyên viên cho đơn đã kết thúc.");
        }
    }

    public static void requireNoWork(long count) {
        if (count > 0) {
            throw BookingRuleException.conflict("Chuyên viên còn phụ trách " + count + " đơn.");
        }
    }
}
