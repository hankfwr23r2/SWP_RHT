package com.example.racehorse_transport.service;

import java.util.List;

public interface BookingDeadlineReminderService {
    List<Long> overdueBookingIds();

    void remind(Long bookingId);
}
