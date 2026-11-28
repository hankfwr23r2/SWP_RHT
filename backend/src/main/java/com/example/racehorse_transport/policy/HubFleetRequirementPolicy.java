package com.example.racehorse_transport.policy;

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class HubFleetRequirementPolicy {
    private static final int DAYS_PER_WEEK = 7;
    private static final long DAY_MICROS = DAYS.toMicros(1);
    private static final long WEEK_MICROS = DAYS_PER_WEEK * DAY_MICROS;

    private HubFleetRequirementPolicy() {}

    public record WeeklyRun(long originHubId, long destinationHubId, boolean outbound, int durationMinutes,
            int dayOfWeek, LocalTime departureTime, int vehicleCount, int maxStallCount) {}

    public record Rules(int restHours, int sparePercent, int crewPercent, int smallBranchVehicles,
            int minBranchSpare, int branchSharePercent, int branchVehicleStalls, int spareHubMinPairs) {}

    public record Requirement(long linehaul, long spare, long sixSeat, long twoSeat, long branchSpare, long crew) {
        public long branch() {
            return sixSeat + twoSeat + branchSpare;
        }
    }

    public static Map<Long, Requirement> calculate(Collection<Long> hubIds, List<WeeklyRun> runs, Rules rules) {
        var byDirection = runs.stream().collect(Collectors.groupingBy(run -> new Direction(run.originHubId(), run.destinationHubId())));
        Map<Long, HubDemand> demand = new HashMap<>();
        hubIds.forEach(id -> demand.put(id, new HubDemand()));
        for (var entry : byDirection.entrySet()) {
            var direction = entry.getKey();
            var hub = demand.get(direction.origin());
            if (hub == null) continue;

            for (var run : entry.getValue()) {
                long stalls = (long) run.vehicleCount() * run.maxStallCount();
                hub.branchByDay[run.dayOfWeek() - 1] += ceilShare(stalls, rules.branchSharePercent(), rules.branchVehicleStalls());
            }
            var outbound = entry.getValue().stream().filter(WeeklyRun::outbound).toList();
            if (!outbound.isEmpty()) {
                hub.pairs++;
                hub.linehaul += peakLinehaul(outbound, byDirection.getOrDefault(direction.reverse(), List.of()), rules.restHours());
            }
        }
        return demand.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().requirement(rules)));
    }

    private static long peakLinehaul(List<WeeklyRun> outbound, List<WeeklyRun> inbound, int restHours) {
        NavigableMap<Long, Long> returnDurations = new TreeMap<>();
        for (var run : inbound) {
            returnDurations.merge(timeInWeek(run), MINUTES.toMicros(run.durationMinutes()), Math::min);
        }
        long rest = HOURS.toMicros(restHours);
        long occupied = 0;
        NavigableMap<Long, Long> changes = new TreeMap<>();
        for (var run : outbound) {
            long start = timeInWeek(run);
            long outAndRest = MINUTES.toMicros(run.durationMinutes()) + rest;
            long length = cycleEnd(start + outAndRest, outAndRest, rest, returnDurations) - start;
            long weight = run.vehicleCount();

            // Mỗi tuần trọn vẹn chiếm xe suốt tuần. Phần dư tạo khoảng [start, end) trên lịch tuần.
            occupied += length / WEEK_MICROS * weight;
            long remainder = length % WEEK_MICROS;
            if (remainder == 0) continue;

            long end = start + remainder;
            changes.merge(start, weight, Long::sum);
            if (end > WEEK_MICROS) {
                occupied += weight;
                end -= WEEK_MICROS;
            }
            if (end < WEEK_MICROS) changes.merge(end, -weight, Long::sum);
        }
        long peak = occupied;
        for (long change : changes.values()) {
            occupied += change;
            peak = Math.max(peak, occupied);
        }
        return peak;
    }

    private static long cycleEnd(long readyAt, long outAndRest, long rest, NavigableMap<Long, Long> returnDurations) {
        if (returnDurations.isEmpty()) return readyAt + outAndRest;

        long offset = readyAt % WEEK_MICROS;
        long weekStart = readyAt - offset;
        var next = returnDurations.ceilingEntry(offset);
        if (next == null) {
            next = returnDurations.firstEntry();
            weekStart += WEEK_MICROS;
        }
        return weekStart + next.getKey() + next.getValue() + rest;
    }

    private static long timeInWeek(WeeklyRun run) {
        return (run.dayOfWeek() - 1) * DAY_MICROS + NANOSECONDS.toMicros(run.departureTime().toNanoOfDay());
    }

    private static long ceilShare(long count, int percent, int unitSize) {
        long divisor = 100L * unitSize;
        return count / divisor * percent + Math.ceilDiv(count % divisor * percent, divisor);
    }

    private record Direction(long origin, long destination) {
        Direction reverse() {
            return new Direction(destination, origin);
        }
    }

    private static final class HubDemand {
        private final long[] branchByDay = new long[DAYS_PER_WEEK];
        private long linehaul;
        private int pairs;

        Requirement requirement(Rules rules) {
            long spare = pairs >= rules.spareHubMinPairs() ? ceilShare(linehaul, rules.sparePercent(), 1) : 0;
            long sixSeat = Arrays.stream(branchByDay).max().orElseThrow();
            long twoSeat = rules.smallBranchVehicles();
            long branchSpare = Math.max(rules.minBranchSpare(), ceilShare(sixSeat + twoSeat, rules.sparePercent(), 1));
            long crew = ceilShare(linehaul + sixSeat + twoSeat, rules.crewPercent(), 1);
            return new Requirement(linehaul, spare, sixSeat, twoSeat, branchSpare, crew);
        }
    }
}
