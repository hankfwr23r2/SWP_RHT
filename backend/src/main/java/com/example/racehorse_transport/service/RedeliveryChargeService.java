package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.HorseLeg;

public interface RedeliveryChargeService {
    void chargeReadyTrip(HorseLeg replacement);
}
