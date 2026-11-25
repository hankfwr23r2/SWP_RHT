package com.example.racehorse_transport.service;

import java.time.Instant;

// Sự cố giao hụt: ai chịu, và tự đóng khi con ngựa đã tới tay người nhận hoặc đã trả.
public interface MissedDeliveryService {

    boolean isRecipientAbsent(Long horseLegId);

    void resolve(Long bookingHorseId, Instant at, Long actorUserId);
}
