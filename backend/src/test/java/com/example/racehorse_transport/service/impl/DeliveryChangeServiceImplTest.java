package com.example.racehorse_transport.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.racehorse_transport.dto.booking.*;
import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.entity.enums.*;
import com.example.racehorse_transport.exception.*;
import com.example.racehorse_transport.repository.*;
import com.example.racehorse_transport.service.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

class DeliveryChangeServiceImplTest {
    private static final Instant ARRIVAL = Instant.parse("2026-12-02T14:00:00Z");
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final BookingHorseRepository horses = mock(BookingHorseRepository.class);
    private final HorseLegRepository legs = mock(HorseLegRepository.class);
    private final CustomerAddressRepository addresses = mock(CustomerAddressRepository.class);
    private final HubServiceAreaRepository areas = mock(HubServiceAreaRepository.class);
    private final QuotationRepository quotations = mock(QuotationRepository.class);
    private final PaymentRepository paymentRows = mock(PaymentRepository.class);
    private final AuditLogRepository auditRows = mock(AuditLogRepository.class);
    private final SystemConfigService config = mock(SystemConfigService.class);
    private final DeliveryRouteService routes = mock(DeliveryRouteService.class);
    private final QuotationDraftService drafts = mock(QuotationDraftService.class);
    private final PaymentService payments = mock(PaymentService.class);
    private final AuditService audit = mock(AuditService.class);
    private final StaffRepository staff = mock(StaffRepository.class);
    private final NotificationService notices = mock(NotificationService.class);
    private final PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
    private final DeliveryChangeService service = new DeliveryChangeServiceImpl(
            new DeliveryChangeDataServiceImpl(bookings, horses, legs, addresses, areas, quotations, paymentRows, auditRows,
                    config, mock(EntityManager.class)), routes, drafts, payments, audit, staff, notices, tx);
    private final DeliveryChangeRequestDto request = new DeliveryChangeRequestDto(3L, 4L, " Recipient ", " 0901234567 ");
    private final BookingAddress target = new BookingAddress();
    private final CustomerAddress source = new CustomerAddress();
    private final Quotation previous = new Quotation();

    @BeforeEach
    void setUp() {
        var user = new AppUser();
        user.setId(1L);
        user.setUsername("owner");
        var customer = new Customer();
        customer.setUserId(1L);
        customer.setUser(user);
        var hub = new Location();
        hub.setLocationType(LocationType.HUB);
        hub.setId(6L);
        var route = new Route();
        route.setDestination(hub);
        route.setEstimatedDurationMinutes(60);
        var departure = new Departure();
        departure.setRoute(route);
        departure.setPlannedDepartureAt(ARRIVAL.minusSeconds(7200));
        var booking = new Booking();
        booking.setId(2L);
        booking.setBookingType(BookingType.INTERPROVINCE);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setDeparture(departure);
        booking.setCustomer(customer);

        var province = new Province();
        province.setCode("A");
        var country = new Country();
        country.setCode("VN");
        target.setId(3L);
        target.setAddressLine("Old address");
        target.setProvince(province);
        target.setCountry(country);
        target.setLatitude(new BigDecimal("10"));
        target.setLongitude(new BigDecimal("106"));
        source.setId(4L);
        source.setAddressLine("New address");
        source.setProvince(province);
        source.setCountry(country);
        source.setLatitude(new BigDecimal("11"));
        source.setLongitude(new BigDecimal("107"));
        var horse = new BookingHorse();
        horse.setId(5L);
        horse.setStatus(BookingHorseStatus.ACTIVE);
        horse.setDeliveryAddress(target);
        var stall = new StallType();
        stall.setCode("STANDARD");
        horse.setStallType(stall);
        previous.setId(9L);

        var deposit = new Payment();
        deposit.setPaymentType(PaymentType.DEPOSIT);
        deposit.setStatus(PaymentStatus.PAID);
        deposit.setAmount(new BigDecimal("20"));
        when(tx.getTransaction(any(TransactionDefinition.class))).thenAnswer(call -> new SimpleTransactionStatus());
        when(bookings.findByIdAndCustomer_User_Username(2L, "owner")).thenReturn(Optional.of(booking));
        when(bookings.findForUpdate(2L)).thenReturn(Optional.of(booking));
        when(paymentRows.findByBooking_IdOrderById(2L)).thenReturn(List.of(deposit));
        when(horses.findForDetail(2L)).thenReturn(List.of(horse));
        when(legs.findLiveLegHorseIds(eq(List.of(5L)), eq(HorseLegType.DELIVERY), any())).thenReturn(Set.of());
        when(quotations.findByBooking_IdAndStatus(2L, QuotationStatus.ACCEPTED)).thenReturn(Optional.of(previous));
        when(addresses.findByIdAndCustomer_UserId(4L, 1L)).thenReturn(Optional.of(source));
        when(areas.findHubId("A", ActiveStatus.ACTIVE)).thenReturn(Optional.of(6L));
        when(config.getInt("DELIVERY_CHANGE_MAX")).thenReturn(2);
        when(config.getInt("MAX_DELIVERY_PLACES")).thenReturn(3);
    }

    @Test
    void previewsAndCommitsWithBookingLockAndReconcilesBalance() {
        var date = LocalDate.of(2026, 12, 2);
        var total = new BigDecimal("34");
        var route = new DeliveryRouteService.DeliveryPricing(null, List.of(), 3600, null);
        var quote = new Quotation();
        quote.setId(10L);
        var price = new DeliveryChangeResponseDto(BigDecimal.TEN, new BigDecimal("14"), new BigDecimal("4"),
                total, new BigDecimal("14"), date, "VND");
        when(routes.pricing(eq(6L), anyList())).thenReturn(route);
        when(routes.arrivalTimes(isNull(), anyList(), eq(ARRIVAL.minusSeconds(3600)))).thenReturn(List.of(ARRIVAL));
        when(drafts.replaceDelivery(eq(previous), anyList(), anyList(), eq(route), eq(date), nullable(BigDecimal.class)))
                .thenReturn(new QuotationDraftService.DeliveryReplacement(quote, price));
        when(staff.findUserIdsByRoleAndStatus("MANAGER", AppUserStatus.ACTIVE)).thenReturn(List.of(60L));
        when(staff.findUserIdsByRoleAndStatus("COORDINATOR", AppUserStatus.ACTIVE)).thenReturn(List.of(61L));

        assertThat(service.preview(2L, "owner", request)).isEqualTo(price);
        assertThat(target.getAddressLine()).isEqualTo("Old address");
        verifyNoInteractions(audit, payments, notices);

        assertThat(service.confirm(2L, "owner", new DeliveryChangeConfirmRequestDto(request, total))).isEqualTo(price);
        assertThat(target.getAddressLine()).isEqualTo("New address");
        assertThat(target.getContactName()).isEqualTo("Recipient");
        assertThat(target.getContactPhone()).isEqualTo("0901234567");
        assertThat(target.getSourceAddress()).isSameAs(source);
        assertThat(target.getId()).isEqualTo(3L);
        verify(bookings).findForUpdate(2L);
        verify(drafts).replaceDelivery(eq(previous), anyList(), anyList(), eq(route), eq(date), eq(total));
        verify(payments).reconcileBalance(quote);
        verify(audit).record(eq(1L), eq("DELIVERY_ADDRESS_CHANGED"), eq("booking"), eq(2L), any(), any());
        verify(notices).notify(argThat(ids -> Set.copyOf(ids).equals(Set.of(60L, 61L))), eq(2L), anyString(), anyString(), isNull());
    }

    @Test
    void rejectsPlannedDeliveryBeforeRouting() {
        when(legs.findLiveLegHorseIds(eq(List.of(5L)), eq(HorseLegType.DELIVERY), any())).thenReturn(Set.of(5L));
        assertThatThrownBy(() -> service.preview(2L, "owner", request))
                .isInstanceOf(BookingRuleException.class).hasMessageContaining("đã có chặng giao");
        verifyNoInteractions(routes, drafts);
    }

    @Test
    void rejectsThirdChangeAndForeignCustomer() {
        when(auditRows.countByTargetTableAndTargetIdAndAction("booking", 2L, "DELIVERY_ADDRESS_CHANGED")).thenReturn(2L);
        var confirmation = new DeliveryChangeConfirmRequestDto(request, BigDecimal.TEN);
        assertThatThrownBy(() -> service.confirm(2L, "owner", confirmation))
                .isInstanceOf(BookingRuleException.class).hasMessageContaining("hết số lần");
        assertThatThrownBy(() -> service.confirm(2L, "other", confirmation))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(routes, drafts, audit, notices);
    }
}
