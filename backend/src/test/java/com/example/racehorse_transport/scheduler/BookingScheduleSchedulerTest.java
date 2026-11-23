package com.example.racehorse_transport.scheduler;

import com.example.racehorse_transport.service.BookingScheduleService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Mỗi lượt quét xét từng đơn tới hạn; một đơn lỗi không được chặn các đơn còn lại.
class BookingScheduleSchedulerTest {

    private final BookingScheduleService schedules = mock(BookingScheduleService.class);
    private final BookingScheduleScheduler scheduler = new BookingScheduleScheduler(schedules);

    @Test
    void everyCandidateIsChecked() {
        when(schedules.candidates()).thenReturn(List.of(1, 2, 3));

        scheduler.scan();

        verify(schedules).flagOverdue(1);
        verify(schedules).flagOverdue(2);
        verify(schedules).flagOverdue(3);
    }

    @Test
    void oneFailingBookingDoesNotStopTheOthers() {
        when(schedules.candidates()).thenReturn(List.of(1, 2, 3));
        doThrow(new IllegalStateException("lỗi đơn 2")).when(schedules).flagOverdue(2);

        scheduler.scan();

        verify(schedules).flagOverdue(1);
        verify(schedules).flagOverdue(3);
    }

    @Test
    void nothingIsDoneWhenNoBookingIsDue() {
        when(schedules.candidates()).thenReturn(List.of());

        scheduler.scan();

        verify(schedules, never()).flagOverdue(org.mockito.ArgumentMatchers.anyInt());
    }
}
