package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.dto.route.RoadRouteDto;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.util.Collections;

public final class DeliveryRoutePolicy {
    private DeliveryRoutePolicy() {}

    // Index 0 is the hub. The returned order contains only delivery destinations.
    public record Plan(List<Integer> order, double deliveryKm, List<Double> individualKm, List<Long> drives, double returnKm) {}

    public static Optional<Plan> shortest(RoadRouteDto[][] roads, TripSchedulePolicy.Rules rules,
                                          List<Integer> horseCounts) {
        List<Plan> feasible = new ArrayList<>();
        visit(roads, rules, horseCounts, new ArrayList<>(), 0, 0, 0, feasible);
        return feasible.stream().min(Comparator.comparingDouble(Plan::deliveryKm));
    }

    private static void visit(RoadRouteDto[][] roads, TripSchedulePolicy.Rules rules, List<Integer> horseCounts,
                              List<Integer> order, int last, double km, long rideSeconds, List<Plan> feasible) {
        if (order.size() == roads.length - 1) {
            List<Long> drives = new ArrayList<>();
            int from = 0;
            for (int to : order) {
                drives.add(roads[from][to].driveSeconds());
                from = to;
            }
            if (roads[last][0] == null) return;
            drives.add(roads[last][0].driveSeconds());
            if (drives.stream().mapToLong(Long::longValue).sum() > TripSchedulePolicy.rideLimitSeconds(true, rules)) return;
            List<Integer> handled = new ArrayList<>();
            handled.add(0);
            order.forEach(place -> handled.add(horseCounts.get(place - 1)));
            handled.add(0);
            var times = TripSchedulePolicy.schedule(Instant.EPOCH, handled, drives, rules);
            long lastArrival = times.get(order.size()).arrival().getEpochSecond();
            if (lastArrival <= TripSchedulePolicy.rideLimitSeconds(false, rules)) {
                List<Double> separate = new ArrayList<>();
                for (int place = 1; place < roads.length; place++) {
                    separate.add(roads[0][place] == null ? null : roads[0][place].km());
                }
                feasible.add(new Plan(List.copyOf(order), km, Collections.unmodifiableList(separate), List.copyOf(drives), roads[last][0].km()));
            }
            return;
        }
        for (int next = 1; next < roads.length; next++) {
            RoadRouteDto road = roads[last][next];
            if (order.contains(next) || road == null) continue;
            long ride = rideSeconds + road.driveSeconds();
            if (ride > TripSchedulePolicy.rideLimitSeconds(false, rules)) continue;
            order.add(next);
            visit(roads, rules, horseCounts, order, next, km + road.km(), ride, feasible);
            order.removeLast();
        }
    }
}
