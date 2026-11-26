package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.enums.DepartureAvailability;
import com.example.racehorse_transport.entity.enums.DepartureStatus;
import com.example.racehorse_transport.exception.BookingRuleException;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DepartureFleetPolicy {
    private DepartureFleetPolicy() {
    }

    public record Stock(Long vehicleClassId, int stalls, int count) {
    }

    public static java.util.Optional<Stock> selectPrivateVehicle(int stalls, List<Stock> stock) {
        return stock.stream().filter(item -> item.count() > 0 && item.stalls() >= stalls)
                .min(Comparator.comparingInt(Stock::stalls).thenComparing(Stock::vehicleClassId));
    }

    public static Instant busyUntil(Instant startsAt, int durationMinutes, int restHours) {
        return startsAt.plusSeconds(120L * durationMinutes + 3600L * restHours);
    }

    public static int capacity(int count, int maxStalls) {
        return Math.multiplyExact(count, maxStalls);
    }

    public static long spareCapacity(List<Stock> stock) {
        return stock.stream().mapToLong(item -> (long) item.count() * item.stalls()).sum();
    }

    public static Map<Stock, Integer> selectSpareVehicles(int shortage, List<Stock> stock) {
        if (spareCapacity(stock) < shortage) {
            return Map.of();
        }
        List<Stock> ordered = stock.stream().sorted(Comparator.comparingInt(Stock::stalls)
                .thenComparing(Stock::vehicleClassId)).toList();
        Map<Stock, Integer> selected = new LinkedHashMap<>();
        while (shortage > 0) {
            int missing = shortage;
            List<Stock> remaining = ordered.stream()
                    .filter(item -> selected.getOrDefault(item, 0) < item.count()).toList();
            Stock chosen = remaining.stream().filter(item -> item.stalls() >= missing)
                    .findFirst().orElseGet(remaining::getLast);
            selected.merge(chosen, 1, Integer::sum);
            shortage -= chosen.stalls();
        }
        return selected;
    }

    public static DepartureAvailability availability(int capacity, int booked, long spare, int threshold) {
        if (spare > 0) {
            return DepartureAvailability.AVAILABLE;
        }
        if (booked >= capacity) {
            return DepartureAvailability.FULL;
        }
        return booked * 100L >= capacity * (long) threshold
                ? DepartureAvailability.NEARLY_FULL : DepartureAvailability.AVAILABLE;
    }

    public static int capacityAfterRemoval(DepartureStatus status, int allotments, int capacity,
                                           int removedCapacity, int booked) {
        int remaining = capacity - removedCapacity;
        if (!DepartureCapacityPolicy.canCancel(status) || allotments <= 1 || remaining < booked) {
            throw BookingRuleException.conflict("Không thể bỏ xe vì chuyến đã chạy, đã hủy hoặc sẽ thiếu xe hay chỗ đã đặt.");
        }
        return remaining;
    }
}
