package com.example.racehorse_transport.service;

import java.util.List;

public interface ExtraChargeService {
    Long createExtraCharge(Long bookingId, List<Long> costItemIds, Long actorUserId);

    Long createExtraChargeForUser(Long bookingId, List<Long> costItemIds, String username);
}
