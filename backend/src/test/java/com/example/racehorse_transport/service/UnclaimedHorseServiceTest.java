package com.example.racehorse_transport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.example.racehorse_transport.dto.operation.UnclaimedHorseResolutionRequestDto;
import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.entity.enums.*;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.policy.BalanceHoldPolicy;
import com.example.racehorse_transport.repository.*;
import com.example.racehorse_transport.service.impl.UnclaimedHorseServiceImpl;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class UnclaimedHorseServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-25T03:00:00Z");
    private static final Instant START = NOW.minus(52, ChronoUnit.DAYS);
    private final HorseStayRepository stays = mock(HorseStayRepository.class);
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final StaffRepository staff = mock(StaffRepository.class);
    private final SystemConfigService configs = mock(SystemConfigService.class);
    private final HorseLegRepository legs = mock(HorseLegRepository.class);
    private final UnclaimedHandoverService handover = mock(UnclaimedHandoverService.class);
    private final BookingStatusService statuses = mock(BookingStatusService.class);
    private final UnclaimedSettlementService settlement = mock(UnclaimedSettlementService.class);
    private final UnclaimedHorseService service = new UnclaimedHorseServiceImpl(stays, bookings, staff, configs, legs,
            handover, statuses, Clock.fixed(NOW, ZoneOffset.UTC), settlement);
    private final HorseStay stay = new HorseStay();
    private final BookingHorse horse = new BookingHorse();
    private final HorseLeg failed = new HorseLeg();
    private final Staff manager = mock(Staff.class, RETURNS_DEEP_STUBS);
    private final UnclaimedHorseResolutionRequestDto request =
            new UnclaimedHorseResolutionRequestDto(UnclaimedHorseOutcome.RETURNED_TO_SENDER, "Sender");
    private final MockMultipartFile proof = new MockMultipartFile("proof", "proof.pdf", "application/pdf", new byte[] {1});

    @BeforeEach
    void setUp() {
        var booking = new Booking();
        booking.setId(10L);
        booking.setStatus(BookingStatus.IN_OPERATION);
        horse.setId(20L);
        horse.setBooking(booking);
        horse.setStatus(BookingHorseStatus.ACTIVE);
        stay.setBookingHorse(horse);
        stay.setStayType(HorseStayType.STABLING);
        stay.setPlannedStartAt(START);
        stay.setActualStartAt(START);
        var location = new Location();
        location.setLocationType(LocationType.HUB);
        stay.setLocation(location);
        failed.setStatus(HorseLegStatus.FAILED);

        when(staff.findByUser_Username("manager")).thenReturn(Optional.of(manager));
        when(manager.getUser().getRole().getCode()).thenReturn("MANAGER");
        when(manager.getUserId()).thenReturn(40L);
        when(stays.findBookingId(30L)).thenReturn(Optional.of(10L));
        when(bookings.findForUpdate(10L)).thenReturn(Optional.of(booking));
        when(stays.findForUpdate(30L)).thenReturn(Optional.of(stay));
        when(configs.getInt("STABLING_WRITTEN_NOTICE_DAYS")).thenReturn(7);
        when(configs.getInt("STABLING_MANAGER_NOTICE_DAYS")).thenReturn(45);
        when(legs.findByBookingHorse_IdOrderBySequenceNumber(20L)).thenReturn(List.of(failed));
    }

    @Test
    void canCancelOnlyAfterReturningTheLastHorseWithNoDelivery() {
        sentAt(NOW.minus(45, ChronoUnit.DAYS));
        service.resolve(30L, request, proof, "manager");

        verify(handover).record(stay, failed, manager, request, proof, NOW);
        assertThat(horse.getStatus()).isEqualTo(BookingHorseStatus.RETURNED);
        var order = inOrder(settlement, statuses);
        order.verify(settlement).settle(10L);
        order.verify(statuses).cancelAfterUnclaimedReturns(10L, 40L);
    }

    @Test
    void aDeliveredHorseMakesTheOrderDelivered() {
        sentAt(NOW.minus(45, ChronoUnit.DAYS));
        when(legs.hasDeliveredHorse(10L)).thenReturn(true);
        service.resolve(30L, request, proof, "manager");

        verify(statuses).transition(eq(10L), eq(BookingStatus.DELIVERED), anyString(), eq(40L));
        verifyNoInteractions(settlement);
    }

    @Test
    void rejectsEarlyResolutionAndMissingProof() {
        sentAt(NOW.minus(45, ChronoUnit.DAYS).plusSeconds(1));
        assertThatThrownBy(() -> service.resolve(30L, request, proof, "manager")).isInstanceOf(BookingRuleException.class);
        assertThatThrownBy(() -> service.resolve(30L, request, null, "manager")).isInstanceOf(BookingRuleException.class);
        assertThat(horse.getStatus()).isEqualTo(BookingHorseStatus.ACTIVE);
        verifyNoInteractions(handover, statuses, settlement);
    }

    private void sentAt(Instant sent) {
        when(bookings.firstNoticeSince(10L, BalanceHoldPolicy.WRITTEN_NOTICE_TITLE, START.plus(7, ChronoUnit.DAYS)))
                .thenReturn(Optional.of(sent));
    }
}
