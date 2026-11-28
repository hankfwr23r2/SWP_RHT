package com.example.racehorse_transport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.example.racehorse_transport.dto.departure.DepartureDelayRequestDto;
import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Route;
import com.example.racehorse_transport.entity.enums.DepartureStatus;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.DepartureRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.service.impl.DepartureDelayServiceImpl;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

class DepartureDelayServiceTest {
    private static final Instant T = Instant.parse("2027-01-15T03:00:00Z");
    private final DepartureRepository departures = mock(DepartureRepository.class);
    private final DeparturePlanShiftService planner = mock(DeparturePlanShiftService.class);
    private final DepartureDelayService service = new DepartureDelayServiceImpl(departures, mock(BookingRepository.class),
            mock(StaffRepository.class), mock(SystemConfigService.class), mock(NotificationService.class), mock(DepartureService.class),
            planner, Clock.fixed(T, ZoneOffset.UTC), mock(TransactionTemplate.class));

    @Test
    void secondRevisionShiftsOnlyByTheDifferenceAndPreservesTheOriginal() {
        var departure = departure(DepartureStatus.CLOSED);
        departure.setRevisedDepartureAt(T.plus(Duration.ofHours(3)));
        when(departures.findWithRoute(15L)).thenReturn(Optional.of(departure));
        service.revise(15L, new DepartureDelayRequestDto(T.plus(Duration.ofHours(5)), "Chờ xe thay thế"));
        verify(planner).shift(15L, Set.of(), Duration.ofHours(2));
        assertThat(departure.getPlannedDepartureAt()).isEqualTo(T);
        assertThat(departure.getRevisedDepartureAt()).isEqualTo(T.plus(Duration.ofHours(5)));
        assertThat(departure.getDelayedAt()).isEqualTo(T);
    }

    @Test
    void alreadyRunningDepartureCannotShiftPlans() {
        departure(DepartureStatus.IN_PROGRESS);
        assertThatThrownBy(() -> service.revise(15L, new DepartureDelayRequestDto(T.plusSeconds(1), "Trễ")))
                .isInstanceOf(BookingRuleException.class);
        verify(planner, never()).shift(anyLong(), anySet(), any());
    }

    private Departure departure(DepartureStatus status) {
        var departure = new Departure();
        var route = new Route();
        route.setOrigin(new Location());
        route.setDestination(new Location());
        departure.setRoute(route);
        departure.setId(15L);
        departure.setPlannedDepartureAt(T);
        departure.setStatus(status);
        departure.setStallCapacity(9);
        when(departures.findForUpdate(15L)).thenReturn(Optional.of(departure));
        return departure;
    }
}
