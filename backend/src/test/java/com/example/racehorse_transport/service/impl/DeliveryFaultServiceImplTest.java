package com.example.racehorse_transport.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.racehorse_transport.dto.incident.HandoverCauseRequestDto;
import com.example.racehorse_transport.dto.incident.HandoverCauseRequestDto.Cause;
import com.example.racehorse_transport.entity.AppUser;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.HorseStay;
import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.Role;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.enums.CostCategory;
import com.example.racehorse_transport.entity.enums.HorseStayType;
import com.example.racehorse_transport.entity.enums.IncidentCauseType;
import com.example.racehorse_transport.entity.enums.IncidentType;
import com.example.racehorse_transport.entity.enums.LocationType;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.CostItemRepository;
import com.example.racehorse_transport.repository.HorseStayRepository;
import com.example.racehorse_transport.repository.IncidentRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.service.AuditService;
import com.example.racehorse_transport.service.IncidentService;
import com.example.racehorse_transport.service.NotificationService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class DeliveryFaultServiceImplTest {
    private final Instant now = Instant.parse("2026-11-25T05:00:00Z");
    private final Incident incident = new Incident();
    private final HorseStay stay = new HorseStay();
    private final Role role = new Role();
    private final IncidentRepository incidents = mock(IncidentRepository.class);
    private final StaffRepository staff = mock(StaffRepository.class);
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final HorseStayRepository stays = mock(HorseStayRepository.class);
    private final CostItemRepository costs = mock(CostItemRepository.class);
    private final SystemConfigService configs = mock(SystemConfigService.class);
    private final AuditService audit = mock(AuditService.class);
    private final NotificationService notices = mock(NotificationService.class);
    private final DeliveryFaultServiceImpl service = new DeliveryFaultServiceImpl(incidents, staff, bookings, stays, costs,
            configs, audit, notices, mock(IncidentService.class), Clock.fixed(now, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        role.setCode("MANAGER");
        var user = new AppUser();
        user.setId(10L);
        user.setRole(role);
        var actor = new Staff();
        actor.setUserId(10L);
        actor.setUser(user);
        var customer = new Customer();
        customer.setUser(user);
        var booking = new Booking();
        booking.setId(2L);
        booking.setCustomer(customer);
        var horse = new BookingHorse();
        horse.setId(3L);
        var leg = new HorseLeg();
        leg.setId(5L);
        leg.setBookingHorse(horse);
        leg.setActualEndAt(now.minusSeconds(1800));
        incident.setIncidentType(IncidentType.HANDOVER_TIMEOUT);
        incident.setReportedAt(leg.getActualEndAt());
        incident.setHorseLeg(leg);
        incident.setBooking(booking);
        incident.setCauseType(IncidentCauseType.COMPANY_FAULT);
        incident.setConfirmedCauseType(IncidentCauseType.COMPANY_FAULT);
        var hub = new Location();
        hub.setLocationType(LocationType.HUB);
        stay.setLocation(hub);
        stay.setId(4L);
        stay.setStayType(HorseStayType.ASSEMBLY);
        stay.setActualStartAt(now.minusSeconds(900));
        when(incidents.findForUpdate(1L)).thenReturn(Optional.of(incident));
        when(staff.findByUser_Username("operator")).thenReturn(Optional.of(actor));
        when(bookings.findForUpdate(2L)).thenReturn(Optional.of(booking));
        when(stays.findReturnStaysForUpdate(3L, 5L)).thenReturn(List.of(stay));
        when(configs.getInt("STABLING_DAILY_FEE")).thenReturn(500000);
        when(configs.getInt("STABLING_MAX_DAYS")).thenReturn(7);
    }

    @Test
    void amendsOnceAndSnapshotsStablingFeeStartingAtHubArrival() {
        amend(Cause.RECIPIENT_ABSENT);
        amend(Cause.RECIPIENT_ABSENT);

        assertThat(incident.getConfirmedCauseType()).isEqualTo(IncidentCauseType.RECIPIENT_ABSENT);
        assertThat(incident.getCauseType()).isEqualTo(IncidentCauseType.COMPANY_FAULT);
        assertThat(stay.getStayType()).isEqualTo(HorseStayType.STABLING);
        assertThat(stay.getStablingUnitPrice()).isEqualByComparingTo("500000");
        assertThat(stay.getActualStartAt()).isEqualTo(now.minusSeconds(900));
        assertThat(stay.getPlannedEndAt()).isEqualTo(stay.getActualStartAt().plusSeconds(7 * 86400));
        verify(audit).record(eq(10L), eq("AMEND_HANDOVER_CAUSE"), eq("incident"), eq(1L), any(), any());
        verify(notices).notify(eq(List.of(10L)), eq(2L), anyString(), anyString(), eq("/orders/2"));
        verify(stays).findReturnStaysForUpdate(3L, 5L);
    }

    @Test
    void freezesCauseOnceStayHasAStablingCost() {
        amend(Cause.RECIPIENT_ABSENT);
        when(costs.existsByHorseStay_IdAndCategory(4L, CostCategory.STABLING)).thenReturn(true);
        assertThatThrownBy(() -> amend(Cause.COMPANY_FAULT)).isInstanceOfSatisfying(
                DispatchRuleException.class, exception -> assertThat(exception.isConflict()).isTrue());
        assertThat(incident.getConfirmedCauseType()).isEqualTo(IncidentCauseType.RECIPIENT_ABSENT);
        assertThat(stay.getStayType()).isEqualTo(HorseStayType.STABLING);
    }

    @Test
    void coordinatorCanConvertUnbilledStablingToFreeAssemblyForForceMajeure() {
        amend(Cause.RECIPIENT_ABSENT);
        role.setCode("COORDINATOR");
        assertThatThrownBy(() -> amend(Cause.COMPANY_FAULT)).isInstanceOf(AccessDeniedException.class);
        amend(Cause.FORCE_MAJEURE);
        assertThat(stay.getStayType()).isEqualTo(HorseStayType.ASSEMBLY);
        assertThat(stay.getStablingUnitPrice()).isNull();
        assertThat(incident.getConfirmedCauseType()).isEqualTo(IncidentCauseType.FORCE_MAJEURE);
    }

    @Test
    void refusesChangesAfterDeadline() {
        incident.getHorseLeg().setActualEndAt(now.minusSeconds(86401));
        assertThatThrownBy(() -> amend(Cause.RECIPIENT_ABSENT)).isInstanceOf(DispatchRuleException.class);
        verifyNoInteractions(stays, audit, notices);
    }

    @Test
    void freezesBilledCauseAfterTheHorseHasLeftTheHub() {
        stay.setActualEndAt(now);
        when(costs.existsByHorseStay_IdAndCategory(4L, CostCategory.STABLING)).thenReturn(true);

        assertThatThrownBy(() -> amend(Cause.RECIPIENT_ABSENT)).isInstanceOfSatisfying(
                DispatchRuleException.class, exception -> assertThat(exception.isConflict()).isTrue());
        verifyNoInteractions(audit, notices);
    }

    private void amend(Cause cause) {
        service.amendCause(1L, new HandoverCauseRequestDto(cause, "Reviewed handover"), "operator");
    }
}
