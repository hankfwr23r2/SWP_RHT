package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.enums.PickupMissReason;
import com.example.racehorse_transport.exception.DispatchRuleException;
import java.time.Duration;
import java.time.Instant;

public final class PickupMissPolicy {
    private PickupMissPolicy() {}

    public static void requireEvidence(PickupMissReason reason, Instant arrivedAt, Instant at, Duration wait,
            String senderContact, String customerContact, String emergencyContact) {
        if ((reason == PickupMissReason.SENDER_ABSENT || reason == PickupMissReason.HORSE_WILL_NOT_BOARD)
                && (arrivedAt == null || at.isBefore(arrivedAt.plus(wait)))) {
            throw new DispatchRuleException("reasonCode", "Chưa đủ thời gian chờ tại điểm đón.", true);
        }
        if (reason == PickupMissReason.SENDER_ABSENT
                && (blank(senderContact) || blank(customerContact) || blank(emergencyContact))) {
            throw new DispatchRuleException("senderContact", "Ghi lần gọi người gửi, khách và liên hệ khẩn cấp.");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public static boolean retryFits(Instant readyAt, Instant tripStartAt, Instant hubArrivalAt, Instant checkInAt) {
        return !tripStartAt.isBefore(readyAt) && !hubArrivalAt.isAfter(checkInAt);
    }

    public static Instant choiceDueAt(Instant missedAt, Instant checkInAt, Duration longWindow, Duration shortWindow) {
        if (checkInAt == null) return missedAt.plus(longWindow);
        if (!checkInAt.isAfter(missedAt)) return missedAt;
        if (checkInAt.isAfter(missedAt.plus(longWindow.multipliedBy(2)))) {
            return missedAt.plus(longWindow);
        }
        Instant shortDue = missedAt.plus(shortWindow);
        return shortDue.isBefore(checkInAt) ? shortDue : checkInAt;
    }
}
