package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.health.StablingClockResponseDto;
import com.example.racehorse_transport.entity.HorseStay;
import java.time.Instant;

public interface StablingChargeService {

    void chargeReturnedBalanceHolds(Long bookingId);
    java.math.BigDecimal accrued(java.util.List<HorseStay> stays, Instant through);
    void warnBeforeNextBlock(java.util.List<HorseStay> stays, Instant now);
    int remindOverdueStabling();
    StablingClockResponseDto clock(HorseStay stay, Instant now, boolean canReady, boolean canCollect);
    void requirePaid(HorseStay stay);
    void chargeAfterReadiness(HorseStay stay, Instant through);
    void chargeThrough(HorseStay stay, Instant through, Long actorUserId);
}
