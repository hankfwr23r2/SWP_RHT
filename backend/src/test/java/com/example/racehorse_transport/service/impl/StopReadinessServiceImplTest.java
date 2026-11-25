package com.example.racehorse_transport.service.impl;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.TripStopType;
import com.example.racehorse_transport.entity.enums.VehicleTripType;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.repository.HandoverHorseRepository;
import com.example.racehorse_transport.repository.HandoverRepository;
import com.example.racehorse_transport.repository.HorseHealthLogRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.service.AuditService;
import com.example.racehorse_transport.service.PaymentReadinessService;
import com.example.racehorse_transport.service.PickupBalanceService;
import com.example.racehorse_transport.service.StablingTimelineService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StopReadinessServiceImplTest {
    private final Instant now = Instant.parse("2026-12-10T01:00:00Z");
    private final HorseLegRepository legs = mock(HorseLegRepository.class);
    private final TripStop stop = new TripStop();
    private final StopReadinessServiceImpl service = new StopReadinessServiceImpl(mock(HandoverRepository.class),
            mock(HandoverHorseRepository.class), legs, mock(HorseHealthLogRepository.class), mock(PaymentReadinessService.class),
            mock(SystemConfigService.class), mock(AuditService.class), Clock.fixed(now, ZoneOffset.UTC),
            mock(PickupBalanceService.class), mock(StablingTimelineService.class));

    @BeforeEach
    void setUp() {
        var trip = new VehicleTrip();
        trip.setTripType(VehicleTripType.DELIVERY);
        stop.setId(7L);
        stop.setVehicleTrip(trip);
        stop.setStopType(TripStopType.DELIVERY);
        stop.setActualArrivalAt(now.minusSeconds(600));
    }

    @Test
    void cannotLeaveDeliveryStopWhileAHorseIsNotDelivered() {
        when(legs.findByAlightTripStop_IdAndStatusIn(7L, Set.of(HorseLegStatus.IN_PROGRESS))).thenReturn(List.of(new HorseLeg()));

        assertThatThrownBy(() -> service.requireCanLeave(stop, null, 1L))
                .isInstanceOf(DispatchRuleException.class).hasMessageContaining("Còn ngựa chưa giao");
    }

    @Test
    void leavesDeliveryStopOnceEveryHorseIsDeliveredOrFailed() {
        when(legs.findByAlightTripStop_IdAndStatusIn(7L, Set.of(HorseLegStatus.IN_PROGRESS))).thenReturn(List.of());

        assertThatCode(() -> service.requireCanLeave(stop, null, 1L)).doesNotThrowAnyException();
    }
}
