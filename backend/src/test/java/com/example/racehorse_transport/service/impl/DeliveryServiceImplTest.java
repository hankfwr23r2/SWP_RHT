package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.field.DeliveryArriveRequestDto;
import com.example.racehorse_transport.dto.field.DeliveryTimeoutRequestDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.repository.BookingAssignmentRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.BookingVehicleRepository;
import com.example.racehorse_transport.repository.IncidentRepository;
import com.example.racehorse_transport.repository.PaymentRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.VehicleRepository;
import com.example.racehorse_transport.service.HorseDeliveryLocationService;
import com.example.racehorse_transport.service.OperationalLogService;
import com.example.racehorse_transport.service.StaffNotificationService;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryServiceImplTest {

    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final IncidentRepository incidents = mock(IncidentRepository.class);
    private final HorseDeliveryLocationService locations = mock(HorseDeliveryLocationService.class);
    private final StaffNotificationService notifier = mock(StaffNotificationService.class);

    private final DeliveryServiceImpl service = new DeliveryServiceImpl(
            bookingRepository,
            mock(VehicleRepository.class),
            mock(BookingVehicleRepository.class),
            mock(BookingAssignmentRepository.class),
            incidents,
            mock(PaymentRepository.class),
            mock(OperationalLogService.class),
            locations,
            mock(StaffRepository.class), notifier, new BookingVisibilityServiceImpl());

    @Test
    void unreadableDetailsAreNotOverwrittenWhenTheCrewArrives() {
        String corrupt = "{\"trips\": [{\"tripId\": \"TRP-001-1\"";
        Booking booking = new Booking();
        booking.setId(1);
        booking.setStatus("in_transit");
        booking.setDetailsJson(corrupt);
        when(bookingRepository.findByIdForUpdate(1)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.arriveDelivery(1, new DeliveryArriveRequestDto()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(booking.getDetailsJson()).isEqualTo(corrupt);
        assertThat(booking.getStatus()).isEqualTo("in_transit");
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void stablingRequiresStableNameAddressPhoneAndPhotoButNoCost() {
        Booking booking = atDelivery();
        for (String missing : new String[] { "name", "address", "phone", "photo" }) {
            var request = stableRequest();
            switch (missing) {
                case "name" -> request.setPartnerName(" ");
                case "address" -> request.setPartnerAddress(null);
                case "phone" -> request.setPartnerPhone("");
                default -> request.setPhotoUrl(null);
            }
            assertThatThrownBy(() -> service.reportDeliveryTimeout(1, request, "admin", "ADMIN"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void stablingTheHorseCountsAsDeliveredSendsTheCrewHomeAndTellsTheCustomerWhereTheHorseIs() {
        Booking booking = atDelivery();
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.reportDeliveryTimeout(1, stableRequest(), "admin", "ADMIN");

        assertThat(booking.getStatus()).isEqualTo("delivered_pending_settlement");
        assertThat(booking.getDetailsJson()).contains("\"deliveredAt\"").contains("\"homeReturn\"")
                .contains("Trại ngựa Đức Hòa").contains("Ấp 3, Đức Hòa, Long An").contains("0901234567");
        var incident = org.mockito.ArgumentCaptor.forClass(com.example.racehorse_transport.entity.Incident.class);
        verify(incidents).save(incident.capture());
        assertThat(incident.getValue().getStatus()).isEqualTo("resolved");
        assertThat(incident.getValue().getEstimatedCost()).isNull();
        verify(locations).recordTripStabling(any(), any(), any(), any(), org.mockito.ArgumentMatchers.eq("https://example.test/stable.jpg"),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.eq("Trại ngựa Đức Hòa, Ấp 3, Đức Hòa, Long An"));
        var message = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(notifier).toCustomer(any(), any(), message.capture());
        assertThat(message.getValue()).contains("Trại ngựa Đức Hòa").contains("Ấp 3, Đức Hòa, Long An").contains("0901234567");
    }

    private Booking atDelivery() {
        Booking booking = new Booking();
        booking.setId(1);
        booking.setCode("BK-AAAA0001");
        booking.setStatus("arrived");
        booking.setDropoffAddress("Điểm giao");
        booking.setDetailsJson("""
            {"horses":[{"horseId":"H1","name":"Storm"}],
             "trips":[{"tripId":"T1","horseIds":["H1"],"run":{"startedAt":100,
               "checkpoints":[{"id":"pickup","type":"pickup","doneAt":100},{"id":"delivery","type":"delivery","arrivedAt":200,"photo":"a.jpg"}]}}]}
            """);
        when(bookingRepository.findByIdForUpdate(1)).thenReturn(Optional.of(booking));
        return booking;
    }

    private static DeliveryTimeoutRequestDto stableRequest() {
        var request = new DeliveryTimeoutRequestDto();
        request.setTripId("T1");
        request.setConfirmEarly(true);
        request.setSelectedAction("STABLE_NEARBY");
        request.setPartnerName("Trại ngựa Đức Hòa");
        request.setPartnerAddress("Ấp 3, Đức Hòa, Long An");
        request.setPartnerPhone("0901234567");
        request.setPhotoUrl("https://example.test/stable.jpg");
        return request;
    }
}
