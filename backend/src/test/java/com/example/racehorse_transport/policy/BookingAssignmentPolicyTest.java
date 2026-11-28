package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.AppUser;
import com.example.racehorse_transport.entity.Role;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.EmploymentStatus;
import com.example.racehorse_transport.exception.BookingRuleException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.http.HttpStatus.*;

class BookingAssignmentPolicyTest {
    @Test
    void distinguishesMissingAssignmentFromAnotherSpecialist() {
        var actor = new AppUser();
        actor.setId(2L);
        var role = new Role();
        role.setCode("SPECIALIST");
        actor.setRole(role);
        var specialist = new Staff();
        specialist.setUserId(1L);
        assertThatThrownBy(() -> BookingAssignmentPolicy.requireAssigned(null, actor))
                .isInstanceOfSatisfying(BookingRuleException.class, error -> {
                    assertThat(error.getStatusCode()).isEqualTo(CONFLICT);
                    assertThat(error.getReason()).isEqualTo("Đơn chưa có chuyên viên phụ trách.");
                });
        assertThatThrownBy(() -> BookingAssignmentPolicy.requireAssigned(specialist, actor))
                .isInstanceOfSatisfying(BookingRuleException.class, error -> {
                    assertThat(error.getStatusCode()).isEqualTo(FORBIDDEN);
                    assertThat(error.getReason()).isEqualTo("Đơn chưa được giao cho bạn.");
                });
        actor.setId(1L);
        assertThat(BookingAssignmentPolicy.requireAssigned(specialist, actor)).isSameAs(specialist);
    }

    @Test
    void rejectsUnavailableSpecialistsAndClosedBookings() {
        var actor = new AppUser();
        var role = new Role();
        role.setCode("SPECIALIST");
        actor.setRole(role);
        actor.setStatus(AppUserStatus.ACTIVE);
        var specialist = new Staff();
        specialist.setUser(actor);
        specialist.setEmploymentStatus(EmploymentStatus.ACTIVE);
        assertThatCode(() -> BookingAssignmentPolicy.requireAvailable(specialist)).doesNotThrowAnyException();
        specialist.setEmploymentStatus(EmploymentStatus.ON_LEAVE);
        assertThatThrownBy(() -> BookingAssignmentPolicy.requireAvailable(specialist)).isInstanceOf(BookingRuleException.class);
        assertThatThrownBy(() -> BookingAssignmentPolicy.requireReassignable(BookingStatus.COMPLETED))
                .isInstanceOf(BookingRuleException.class);
        assertThatCode(() -> BookingAssignmentPolicy.requireReassignable(BookingStatus.QUOTE_EXPIRED)).doesNotThrowAnyException();
        assertThatThrownBy(() -> BookingAssignmentPolicy.requireNoWork(3)).hasMessageContaining("3 đơn");
    }
}
