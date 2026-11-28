package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.enums.LocationType;
import com.example.racehorse_transport.exception.VehicleRuleException;

public final class VehiclePolicy {

    private VehiclePolicy() {}

    public static void requireCapacity(int standardStallCount, int wideStallCount, int maxStallCount) {
        long total = (long) standardStallCount + wideStallCount;
        if (standardStallCount < 0 || wideStallCount < 0 || total < 1 || total > maxStallCount) {
            throw new VehicleRuleException("standardStallCount",
                    "Tổng số khoang phải từ 1 đến " + maxStallCount + " và mỗi loại khoang không được âm.");
        }
    }

    public static void requireHeadquarters(LocationType locationType, boolean headquarters) {
        if (locationType != LocationType.HUB || !headquarters) {
            throw new VehicleRuleException("homeHubLocationId", "Hub gốc của xe phải là trụ sở.");
        }
    }
}
