package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.HorseStay;
import java.time.Instant;

public interface StablingChargeService {
    int remindOverdueStabling();
    void precharge(HorseLeg replacement, String username);
    void requirePaid(HorseStay stay);
    void chargeThrough(HorseStay stay, Instant through, Long actorUserId);
}
