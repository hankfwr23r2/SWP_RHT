package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.health.StablingClockResponseDto;
import java.time.Instant;
import java.util.List;

public interface StablingClockService {
    List<StablingClockResponseDto> forBooking(Long bookingId, String username);
    void ready(Long stayId, String username, String callNote);
    void laterSlot(Long stayId, Instant earliest, Instant chosen);
    void collect(Long stayId, String username, Instant expectedAt);
    void remindMissingRedelivery();
}
