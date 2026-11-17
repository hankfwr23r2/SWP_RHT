package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Log;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.LogRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OperationalLogWriterTest {
    @Test
    void vehicleIdIsNotTreatedAsBookingIdAndBookingEventsKeepTheirRelation() {
        LogRepository logs = mock(LogRepository.class);
        BookingRepository bookings = mock(BookingRepository.class);
        StaffRepository staff = mock(StaffRepository.class);
        OperationalLogWriter writer = new OperationalLogWriter(logs, bookings, staff);
        writer.record("VEHICLE_RETURNED_HQ", "VEHICLE", 42, "Returned", null);
        verifyNoInteractions(bookings);
        Booking booking = new Booking(); booking.setId(42);
        when(bookings.findById(42)).thenReturn(Optional.of(booking));
        writer.record("CREATE_BOOKING", "BOOKING", 42, "Created", "customer");
        var entries = ArgumentCaptor.forClass(Log.class);
        verify(logs, times(2)).save(entries.capture());
        assertThat(entries.getAllValues().get(0).getBookingID()).isNull();
        assertThat(entries.getAllValues().get(0).getTargetTable()).isEqualTo("VEHICLE");
        assertThat(entries.getAllValues().get(1).getBookingID()).isSameAs(booking);
        assertThat(entries.getAllValues().get(1).getActorUsername()).isEqualTo("customer");
        assertThat(entries.getAllValues()).allSatisfy(entry -> assertThat(entry.getId()).isNull());
    }
}
