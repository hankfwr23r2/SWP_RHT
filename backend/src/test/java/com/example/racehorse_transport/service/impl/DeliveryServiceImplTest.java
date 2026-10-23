package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.field.DeliveryArriveRequest;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.repository.BookingAssignmentRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.BookingVehicleRepository;
import com.example.racehorse_transport.repository.HorseJourneyLogRepository;
import com.example.racehorse_transport.repository.HorseRepository;
import com.example.racehorse_transport.repository.IncidentRepository;
import com.example.racehorse_transport.repository.PaymentRepository;
import com.example.racehorse_transport.repository.SystemLogRepository;
import com.example.racehorse_transport.repository.VehicleRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

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
            mock(SystemLogRepository.class),
            mock(HorseRepository.class),
            mock(HorseJourneyLogRepository.class));

    @Test
    void unreadableDetailsAreNotOverwrittenWhenTheCrewArrives() {
        String corrupt = "{\"trips\": [{\"tripId\": \"TRP-001-1\"";
        Booking booking = new Booking();
        booking.setId(1);
        booking.setStatus("in_transit");
        booking.setDetailsJson(corrupt);
        when(bookingRepository.findById(1)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.arriveDelivery(1, new DeliveryArriveRequest()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(booking.getDetailsJson()).isEqualTo(corrupt);
        assertThat(booking.getStatus()).isEqualTo("in_transit");
        verify(bookingRepository, never()).save(any(Booking.class));
    }
}
