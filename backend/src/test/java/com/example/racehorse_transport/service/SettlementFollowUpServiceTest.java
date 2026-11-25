package com.example.racehorse_transport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingStatusHistory;
import com.example.racehorse_transport.entity.CostItem;
import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.CostPayerType;
import com.example.racehorse_transport.entity.enums.IncidentStatus;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.BookingStatusHistoryRepository;
import com.example.racehorse_transport.repository.CostItemRepository;
import com.example.racehorse_transport.repository.IncidentRepository;
import com.example.racehorse_transport.repository.PaymentRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.service.impl.SettlementFollowUpServiceImpl;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SettlementFollowUpServiceTest {
    private static final Instant DELIVERED = Instant.parse("2026-11-25T08:00:00Z");
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final BookingStatusHistoryRepository history = mock(BookingStatusHistoryRepository.class);
    private final CostItemRepository costs = mock(CostItemRepository.class);
    private final IncidentRepository incidents = mock(IncidentRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final StaffRepository staff = mock(StaffRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final SystemConfigService config = mock(SystemConfigService.class);
    private final CostItem cost = new CostItem();

    @BeforeEach
    void deliveredBookingWithPendingCost() {
        var booking = new Booking();
        booking.setId(1L);
        booking.setCode("BK-1");
        booking.setStatus(BookingStatus.DELIVERED);
        when(bookings.findForUpdate(1L)).thenReturn(Optional.of(booking));
        var row = new BookingStatusHistory();
        row.setChangedAt(DELIVERED);
        when(history.findFirstByBooking_IdAndToStatusOrderByChangedAtDescIdDesc(1L, BookingStatus.DELIVERED)).thenReturn(Optional.of(row));
        cost.setId(5L);
        cost.setPayerType(CostPayerType.CUSTOMER);
        cost.setAmount(BigDecimal.TEN);
        when(costs.findByBooking_IdOrderByIncurredAt(1L)).thenReturn(List.of(cost));
        when(costs.findForUpdate(5L)).thenReturn(Optional.of(cost));
        when(staff.findUserIdsByRoleAndStatus("MANAGER", AppUserStatus.ACTIVE)).thenReturn(List.of(9L));
        when(config.getInt("COST_APPROVAL_REMINDER_1_DAYS")).thenReturn(3);
        when(config.getInt("COST_APPROVAL_REMINDER_2_DAYS")).thenReturn(7);
        when(config.getInt("COST_APPROVAL_DAYS")).thenReturn(14);
        when(config.getInt("INCIDENT_AUTO_CLOSE_DAYS")).thenReturn(7);
    }

    private SettlementFollowUpService at(Duration after) {
        return new SettlementFollowUpServiceImpl(bookings, history, costs, incidents, payments, staff, notifications,
                mock(AuditService.class), config, Clock.fixed(DELIVERED.plus(after), ZoneOffset.UTC));
    }

    @Test
    void remindsBeforeTheCompanyTakesUnapprovedCosts() {
        at(Duration.ofDays(8)).processDelivered(1L);
        verify(notifications).notify(eq(List.of(9L)), eq(1L), eq("Chi phí chờ duyệt · ngày 7"), anyString(), anyString());
        assertThat(cost.getPayerType()).isEqualTo(CostPayerType.CUSTOMER);

        at(Duration.ofDays(14)).processDelivered(1L);
        assertThat(cost.getPayerType()).isEqualTo(CostPayerType.COMPANY);
    }

    @Test
    void repeatedRunDoesNotRepeatTheReminder() {
        when(bookings.hasNotificationBetween(eq(1L), anyString(), any(), any())).thenReturn(true);
        at(Duration.ofDays(3)).processDelivered(1L);
        verify(notifications, never()).notify(anyList(), any(), anyString(), anyString(), any());
    }

    @Test
    void resolvedIncidentClosesAfterSevenDaysUnlessACostIsPending() {
        var incident = new Incident();
        incident.setStatus(IncidentStatus.RESOLVED);
        incident.setResolvedAt(DELIVERED);
        when(incidents.findForUpdate(3L)).thenReturn(Optional.of(incident));
        at(Duration.ofDays(7).minusSeconds(1)).closeResolvedIncident(3L);
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        when(costs.findByIncident_IdOrderByIncurredAt(3L)).thenReturn(List.of(cost));
        at(Duration.ofDays(7)).closeResolvedIncident(3L);
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        when(costs.findByIncident_IdOrderByIncurredAt(3L)).thenReturn(List.of());
        at(Duration.ofDays(7)).closeResolvedIncident(3L);
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.CLOSED);
    }
}
