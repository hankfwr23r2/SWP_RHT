package com.example.racehorse_transport.service;

import java.util.List;

public interface BalanceHoldService {
    List<Long> candidates();
    void process(Long bookingId);
}
