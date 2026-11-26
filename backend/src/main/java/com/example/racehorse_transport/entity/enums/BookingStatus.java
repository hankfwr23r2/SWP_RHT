package com.example.racehorse_transport.entity.enums;

// Chỉ gồm mốc thương mại. Tiến độ vận hành suy ra từ horse_leg, horse_stay, handover, incident.
public enum BookingStatus {
    PENDING_INTAKE,
    UNDER_REVIEW,
    NEEDS_RESUBMISSION,
    QUOTING,
    AWAITING_DEPOSIT,
    QUOTE_EXPIRED,
    CONFIRMED,
    SCHEDULE_CHANGE_REQUIRED,
    IN_OPERATION,
    DELIVERED,
    COMPLETED,
    CANCELLED,
    REJECTED
}
