package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.HorseStay;
import java.time.Instant;
import java.util.List;

public interface BalanceHoldNoticeService {
    void remindBalance(Booking booking, Instant reminder, Instant cutoff, Instant now);
    void holdStarted(Booking booking, Instant base);
    void processHeld(Booking booking, List<HorseStay> held, Instant now, boolean paid);
    boolean waitingForGateway(Long bookingId, Instant cutoff, Instant now);
    void releaseForUnpaidHub(Long bookingId);
    List<Long> expiredPickupReturns(Instant now);
    void cancelExpiredPickupReturn(Booking booking, Instant now);
    List<Long> waitingPickupBalances();
    void remindAtPickup(Booking booking, Instant now);
}
