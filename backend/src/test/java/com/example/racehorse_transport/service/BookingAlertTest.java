package com.example.racehorse_transport.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.entity.enums.*;
import com.example.racehorse_transport.repository.*;
import com.example.racehorse_transport.service.impl.CustomerNoticeServiceImpl;
import com.example.racehorse_transport.service.impl.PickupMissEvidenceServiceImpl;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class BookingAlertTest {
    private static final Instant NOW = Instant.parse("2026-11-25T03:00:00Z");
    private final NotificationService notifications = mock(NotificationService.class);
    private final StaffRepository staff = mock(StaffRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final SystemConfigService configs = mock(SystemConfigService.class);

    @Test
    void linkedDebtAlertsManagersOnlyWhenOneAccountMeetsTheThreshold() {
        var booking = booking(1L, 10L);
        var debt = new Payment();
        debt.setBooking(booking(2L, 20L));
        debt.setPaymentType(PaymentType.EXTRA_CHARGE);
        debt.setStatus(PaymentStatus.PENDING);
        debt.setDueAt(NOW.minusSeconds(1));
        debt.setAmount(new BigDecimal("200000"));
        when(configs.getInt("DEBT_BLOCK_MIN_AMOUNT")).thenReturn(200_000);
        when(staff.findUserIdsByRoleAndStatus("MANAGER", AppUserStatus.ACTIVE)).thenReturn(List.of(30L));
        when(payments.findLinkedOverdue(1L, 10L, "0900000000", PaymentType.EXTRA_CHARGE, PaymentStatus.PENDING, NOW))
                .thenReturn(List.of(debt));
        var notices = new CustomerNoticeServiceImpl(notifications, staff, mock(HorseLegRepository.class),
                mock(HorseStayRepository.class), configs, payments, Clock.fixed(NOW, ZoneOffset.UTC));

        notices.bookingSubmitted(booking);
        verify(notifications).notify(eq(List.of(30L)), eq(1L), any(), any(), eq("/manager/orders/1"));

        clearInvocations(notifications);
        debt.setAmount(new BigDecimal("199999"));
        notices.bookingSubmitted(booking);
        verify(notifications, never()).notify(eq(List.of(30L)), any(), any(), any(), any());
    }

    @Test
    void pickupMissAlertsCustomerOnceWithNameReasonDeadlineAndOrderLink() {
        var line = new BookingHorse();
        line.setId(3L);
        line.setBooking(booking(1L, 10L));
        line.setHorse(new Horse());
        line.getHorse().setName("Sao");
        line.setPickupMissReason("Người gửi vắng");
        line.setPickupChoiceDueAt(NOW);
        when(staff.findUserIdsByRoleAndStatus("COORDINATOR", AppUserStatus.ACTIVE)).thenReturn(List.of(10L, 20L));
        var evidence = new PickupMissEvidenceServiceImpl(mock(DocumentService.class), configs,
                mock(AuditService.class), staff, notifications);

        evidence.record(line, null, null, null, NOW.minusSeconds(1), 20L);

        verify(notifications).notify(eq(List.of(10L)), eq(1L), eq("Đón hụt"),
                argThat(message -> message.contains("Sao") && message.contains("Người gửi vắng")
                        && message.contains("10:00 25/11/2026")), eq("/orders/1"));
        verify(notifications).notify(eq(List.of(20L)), eq(1L), eq("Đón hụt"), any(), isNull());
    }

    private static Booking booking(long id, long customerId) {
        var booking = new Booking();
        booking.setId(id);
        booking.setCode("BK-" + id);
        var customer = new Customer();
        customer.setUserId(customerId);
        customer.setPhone("0900000000");
        var user = new AppUser();
        user.setId(customerId);
        customer.setUser(user);
        booking.setCustomer(customer);
        return booking;
    }
}
