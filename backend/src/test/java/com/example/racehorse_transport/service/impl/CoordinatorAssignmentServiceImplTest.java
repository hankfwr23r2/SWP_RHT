package com.example.racehorse_transport.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.racehorse_transport.dto.dispatch.CoordinatorAssignmentRequestDto;
import com.example.racehorse_transport.dto.dispatch.CoordinatorTakeOverRequestDto;
import com.example.racehorse_transport.entity.AppUser;
import com.example.racehorse_transport.entity.CoordinatorAssignmentHistory;
import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.Role;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.EmploymentStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.repository.AppUserRepository;
import com.example.racehorse_transport.repository.CoordinatorAssignmentHistoryRepository;
import com.example.racehorse_transport.repository.CostItemRepository;
import com.example.racehorse_transport.repository.DepartureRepository;
import com.example.racehorse_transport.repository.IncidentRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.VehicleTripRepository;
import com.example.racehorse_transport.service.NotificationService;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CoordinatorAssignmentServiceImplTest {
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final StaffRepository staff = mock(StaffRepository.class);
    private final DepartureRepository departures = mock(DepartureRepository.class);
    private final VehicleTripRepository trips = mock(VehicleTripRepository.class);
    private final IncidentRepository incidents = mock(IncidentRepository.class);
    private final CoordinatorAssignmentHistoryRepository history = mock(CoordinatorAssignmentHistoryRepository.class);
    private final CoordinatorAssignmentServiceImpl service = new CoordinatorAssignmentServiceImpl(users, staff, departures, trips,
            incidents, mock(CostItemRepository.class), history, mock(NotificationService.class), Clock.systemUTC());
    private final VehicleTrip trip = new VehicleTrip();
    private Staff first;
    private Staff second;

    @BeforeEach
    void setUp() {
        first = person(1L, "first", "COORDINATOR");
        second = person(2L, "second", "COORDINATOR");
        person(9L, "manager", "MANAGER");
        trip.setId(5L);
        trip.setCode("VT-5");
        trip.setStatus(VehicleTripStatus.PLANNED);
        when(trips.findForUpdate(5L)).thenReturn(Optional.of(trip));
    }

    @Test
    void firstWriterOfAnOldTripBecomesItsOwner() {
        service.requireTripWrite(5L, "first");

        assertThat(trip.getCoordinator()).isSameAs(first);
        verify(history).save(any(CoordinatorAssignmentHistory.class));
    }

    @Test
    void anotherCoordinatorCannotWriteAndTheErrorNamesTheOwner() {
        trip.setCoordinator(first);

        assertThatThrownBy(() -> service.requireTripWrite(5L, "second"))
                .isInstanceOf(BookingRuleException.class).hasMessageContaining("Điều phối first");
    }

    @Test
    void takesOverOnlyARunningTripOrOneWithAnOpenIncident() {
        trip.setCoordinator(first);
        var request = new CoordinatorTakeOverRequestDto("Không liên lạc được");
        assertThatThrownBy(() -> service.takeOver(5L, request, "second")).isInstanceOf(BookingRuleException.class);

        when(incidents.countOpenForTrip(anyLong(), any())).thenReturn(1L);
        service.takeOver(5L, request, "second");

        assertThat(trip.getCoordinator()).isSameAs(second);
    }

    @Test
    void changingTheDepartureCoordinatorMovesItsOpenTripsAndNeedsAReason() {
        var departure = new Departure();
        departure.setCoordinator(first);
        trip.setCoordinator(first);
        when(departures.findForUpdate(7L)).thenReturn(Optional.of(departure));
        when(trips.findByDeparture_IdAndTripTypeAndStatusNotInOrderById(anyLong(), any(), any())).thenReturn(List.of(trip));
        assertThatThrownBy(() -> service.assignDeparture(7L, new CoordinatorAssignmentRequestDto(2L, " "), "manager"))
                .isInstanceOf(BookingRuleException.class);

        service.assignDeparture(7L, new CoordinatorAssignmentRequestDto(2L, "Đổi ca"), "manager");

        assertThat(departure.getCoordinator()).isSameAs(second);
        assertThat(trip.getCoordinator()).isSameAs(second);
    }

    @Test
    void onlyManagersAssign() {
        assertThatThrownBy(() -> service.assignTrip(5L, new CoordinatorAssignmentRequestDto(2L, null), "first"))
                .isInstanceOf(BookingRuleException.class);
    }

    private Staff person(Long id, String username, String roleCode) {
        var role = new Role();
        role.setCode(roleCode);
        var user = new AppUser();
        user.setId(id);
        user.setRole(role);
        user.setStatus(AppUserStatus.ACTIVE);
        var person = new Staff();
        person.setUserId(id);
        person.setUser(user);
        person.setFullName("Điều phối " + username);
        person.setEmploymentStatus(EmploymentStatus.ACTIVE);
        when(users.findByUsername(username)).thenReturn(Optional.of(user));
        when(staff.findForUpdate(id)).thenReturn(Optional.of(person));
        return person;
    }
}
