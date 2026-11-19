package com.example.racehorse_transport.service;

import java.time.LocalDate;
import java.util.List;

public interface BookingScheduleService {

    List<Integer> candidates();

    void flagOverdue(Integer id);

    void reschedule(Integer id, String username, LocalDate departureDate);

    void cancel(Integer id, String username, String reason);

    void requestSupport(Integer id, String username);
}
