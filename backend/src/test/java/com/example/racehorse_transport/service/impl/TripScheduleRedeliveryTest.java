package com.example.racehorse_transport.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.racehorse_transport.dto.dispatch.PlannedTripRequestDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingAddress;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.enums.BookingHorseStatus;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.HorseLegType;
import com.example.racehorse_transport.entity.enums.VehicleTripType;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.repository.BookingHorseRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TripScheduleRedeliveryTest {
    private final BookingHorseRepository horses = mock(BookingHorseRepository.class);
    private final HorseLegRepository legs = mock(HorseLegRepository.class);
    private final TripScheduleDataServiceImpl service =
            new TripScheduleDataServiceImpl(null, horses, null, null, null, null, legs);
    private final PlannedTripRequestDto request = mock(PlannedTripRequestDto.class);

    @BeforeEach
    void bookingWithTwoPlaces() {
        var booking = new Booking();
        booking.setId(1L);
        var first = horse(booking, 11L, 101L);
        var second = horse(booking, 12L, 102L);
        when(horses.findById(11L)).thenReturn(Optional.of(first));
        when(horses.findForDetail(1L)).thenReturn(List.of(first, second));
        when(request.tripType()).thenReturn(VehicleTripType.DELIVERY);
        when(request.places()).thenReturn(List.of());
        when(request.bookingHorseIds()).thenReturn(List.of(11L));
    }

    @Test
    void firstDeliveryNeedsEveryHorse() {
        assertThatThrownBy(() -> service.load(request)).isInstanceOf(DispatchRuleException.class)
                .hasMessageContaining("Chọn tất cả ngựa");
    }

    @Test
    void redeliveryDoesNotNeedTheHorseAlreadyDelivered() {
        when(legs.findByBookingHorse_IdOrderBySequenceNumber(11L)).thenReturn(List.of(leg(HorseLegStatus.FAILED)));
        when(legs.findByBookingHorse_IdOrderBySequenceNumber(12L)).thenReturn(List.of(leg(HorseLegStatus.COMPLETED)));
        // Qua được bước kiểm đủ ngựa; dừng ở bước sau vì yêu cầu mẫu không có điểm dừng
        assertThat(String.valueOf(catchThrowable(() -> service.load(request)))).doesNotContain("Chọn tất cả ngựa");
    }

    private static HorseLeg leg(HorseLegStatus status) {
        var leg = new HorseLeg();
        leg.setLegType(HorseLegType.DELIVERY);
        leg.setStatus(status);
        return leg;
    }

    private static BookingHorse horse(Booking booking, Long id, Long addressId) {
        var address = new BookingAddress();
        address.setId(addressId);
        var horse = new BookingHorse();
        horse.setId(id);
        horse.setBooking(booking);
        horse.setStatus(BookingHorseStatus.ACTIVE);
        horse.setDeliveryAddress(address);
        return horse;
    }
}
