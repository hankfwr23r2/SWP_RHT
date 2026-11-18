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

    private final DeliveryServiceImpl service = new DeliveryServiceImpl(
            bookingRepository,
            mock(VehicleRepository.class),
            mock(BookingVehicleRepository.class),
            mock(BookingAssignmentRepository.class),
            mock(IncidentRepository.class),
            mock(PaymentRepository.class),
            mock(OperationalLogService.class),
            mock(HorseDeliveryLocationService.class),
            mock(StaffRepository.class), org.mockito.Mockito.mock(StaffNotificationService.class), new BookingVisibilityServiceImpl());

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
    void rejectsMissingZeroFractionalAndOverLimitStableCosts() {
        Booking booking = new Booking();
        booking.setId(1);
        booking.setStatus("in_transit");
        when(bookingRepository.findByIdForUpdate(1)).thenReturn(Optional.of(booking));

        for (BigDecimal amount : new BigDecimal[] {
                null,
                BigDecimal.ZERO,
                new BigDecimal("1.5"),
                new BigDecimal("10000000000000000")
        }) {
            var request = new DeliveryTimeoutRequestDto();
            request.setSelectedAction("STABLE_NEARBY");
            request.setEstimatedCost(amount);

            assertThatThrownBy(() -> service.reportDeliveryTimeout(1, request, "driver", "DRIVER"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        verify(bookingRepository, never()).save(any(Booking.class));
    }
}
