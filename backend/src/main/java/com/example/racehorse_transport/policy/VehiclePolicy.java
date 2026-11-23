package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.enums.LocationType;
import com.example.racehorse_transport.entity.enums.VehicleStatus;
import com.example.racehorse_transport.exception.VehicleRuleException;

public final class VehiclePolicy {

    private VehiclePolicy() {}

    public static void requireCapacity(int standardStallCount, int wideStallCount, int maxStallCount) {
        long total = (long) standardStallCount * StallSeatPolicy.seats("STANDARD")
                + (long) wideStallCount * StallSeatPolicy.seats("WIDE");
        if (standardStallCount < 0 || wideStallCount < 0 || total < 1 || total > maxStallCount) {
            throw new VehicleRuleException("standardStallCount",
                    "Tổng số chỗ phải từ 1 đến " + maxStallCount + " và mỗi loại khoang không được âm.");
        }
    }

    public static void requireStatusTransition(VehicleStatus current, VehicleStatus next) {
        boolean allowed = switch (current) {
            case ACTIVE -> next == VehicleStatus.MAINTENANCE || next == VehicleStatus.RETIRED;
            case MAINTENANCE -> next == VehicleStatus.ACTIVE || next == VehicleStatus.RETIRED;
            case RETIRED -> false;
        };
        if (!allowed) {
            throw new VehicleRuleException("status", "Không thể đổi trạng thái xe từ " + current + " sang " + next + ".", true);
        }
    }

    public static void requireHeadquarters(LocationType locationType, boolean headquarters) {
        if (locationType != LocationType.HUB || !headquarters) {
            throw new VehicleRuleException("homeHubLocationId", "Hub gốc của xe phải là trụ sở.");
        }
    }
}
