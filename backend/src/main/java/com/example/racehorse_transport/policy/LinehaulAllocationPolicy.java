package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.policy.TripGroupingPolicy.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class LinehaulAllocationPolicy {
    private static final int REPACK_SEARCH_LIMIT = 50_000;

    private LinehaulAllocationPolicy() {}

    public record BookingLoad(long bookingId, boolean privateVehicle, int seats, List<Point> destinations) {}
    public record Slot(long allotmentId, int number, Long privateBookingId, int capacity) {}
    public record Load(BookingLoad booking, int cluster) {}
    public record Allocation(Slot slot, List<Load> loads) {
        public int seats() {
            return loads.stream().mapToInt(load -> load.booking().seats()).sum();
        }
    }
    public record Plan(List<Allocation> allocations, int missingSeats) {}

    public static Plan suggest(List<BookingLoad> bookings, List<Slot> slots, int soldSharedSeats, double radiusKm) {
        List<Allocation> fleet = slots.stream().sorted(Comparator.comparingInt(Slot::capacity)
                .thenComparingLong(Slot::allotmentId).thenComparingInt(Slot::number))
                .map(slot -> new Allocation(slot, new ArrayList<>())).toList();
        int missingPrivate = 0;
        for (BookingLoad booking : bookings) {
            if (!booking.privateVehicle()) continue;
            var reserved = fleet.stream().filter(vehicle -> Long.valueOf(booking.bookingId()).equals(vehicle.slot().privateBookingId())
                    && vehicle.slot().capacity() >= booking.seats()).findFirst();
            if (reserved.isPresent()) reserved.get().loads().add(new Load(booking, 0));
            else missingPrivate += booking.seats();
        }

        List<List<BookingLoad>> clusters = clusters(bookings, radiusKm);
        int missingShared = 0;
        for (int index = 0; index < clusters.size(); index++) {
            List<BookingLoad> cluster = clusters.get(index);
            int seats = cluster.stream().mapToInt(BookingLoad::seats).sum();
            var wholeCluster = bestFit(fleet, seats, true);
            if (wholeCluster.isPresent()) {
                for (BookingLoad booking : cluster) wholeCluster.get().loads().add(new Load(booking, index + 1));
                continue;
            }
            for (BookingLoad booking : cluster) {
                int clusterId = index + 1;
                var vehicle = fleet.stream().filter(v -> sharedRoom(v, booking.seats())
                                && v.loads().stream().anyMatch(load -> load.cluster() == clusterId))
                        .min(Comparator.comparingInt(v -> v.slot().capacity() - v.seats()))
                        .or(() -> bestFit(fleet, booking.seats(), true))
                        .or(() -> bestFit(fleet, booking.seats(), false));
                if (vehicle.isPresent()) vehicle.get().loads().add(new Load(booking, clusterId));
                else missingShared += booking.seats();
            }
        }
        // Best fit can leave unusable gaps even when every booking could fit on the reserved fleet.
        if (missingShared > 0 && repackShared(fleet, clusters)) missingShared = 0;

        int sharedCapacity = fleet.stream().filter(v -> v.slot().privateBookingId() == null && !v.loads().isEmpty())
                .mapToInt(v -> v.slot().capacity()).sum();
        List<Allocation> selected = new ArrayList<>(fleet.stream().filter(v -> !v.loads().isEmpty()).toList());
        for (Allocation vehicle : fleet) {
            if (sharedCapacity >= soldSharedSeats) break;
            if (vehicle.slot().privateBookingId() != null || !vehicle.loads().isEmpty()) continue;
            selected.add(vehicle);
            sharedCapacity += vehicle.slot().capacity();
        }
        return new Plan(selected.stream().map(v -> new Allocation(v.slot(), List.copyOf(v.loads()))).toList(),
                missingPrivate + Math.max(missingShared, soldSharedSeats - sharedCapacity));
    }

    private static boolean repackShared(List<Allocation> fleet, List<List<BookingLoad>> clusters) {
        var remaining = fleet.stream().filter(v -> v.slot().privateBookingId() == null).toList();
        var trial = remaining.stream().map(v -> new Allocation(v.slot(), new ArrayList<>())).toList();
        List<Load> loads = new ArrayList<>();
        for (int index = 0; index < clusters.size(); index++) {
            for (BookingLoad booking : clusters.get(index)) loads.add(new Load(booking, index + 1));
        }
        int capacity = trial.stream().mapToInt(v -> v.slot().capacity()).sum();
        if (loads.stream().mapToInt(load -> load.booking().seats()).sum() > capacity) return false;
        loads.sort(Comparator.comparingInt((Load load) -> load.booking().seats()).reversed());
        if (!new RepackSearch(trial, loads).place(0)) return false;
        for (int index = 0; index < trial.size(); index++) {
            remaining.get(index).loads().clear();
            remaining.get(index).loads().addAll(trial.get(index).loads());
        }
        return true;
    }

    private static final class RepackSearch {
        private final List<Allocation> fleet;
        private final List<Load> loads;
        private int attempts;

        private RepackSearch(List<Allocation> fleet, List<Load> loads) {
            this.fleet = fleet;
            this.loads = loads;
        }

        private boolean place(int index) {
            if (index == loads.size()) return true;
            if (++attempts > REPACK_SEARCH_LIMIT) return false;
            Load load = loads.get(index);
            var options = fleet.stream().filter(v -> sharedRoom(v, load.booking().seats()))
                    .sorted(Comparator.comparingInt((Allocation v) -> clusterPreference(v, load.cluster()))
                            .thenComparingInt(v -> v.slot().capacity() - v.seats())).toList();
            Set<Integer> triedCapacity = new HashSet<>();
            for (Allocation vehicle : options) {
                if (!triedCapacity.add(vehicle.slot().capacity() - vehicle.seats())) continue;
                vehicle.loads().add(load);
                if (place(index + 1)) return true;
                vehicle.loads().removeLast();
            }
            return false;
        }

        private static int clusterPreference(Allocation vehicle, int cluster) {
            if (vehicle.loads().stream().anyMatch(load -> load.cluster() == cluster)) return 0;
            return vehicle.loads().isEmpty() ? 1 : 2;
        }
    }

    private static java.util.Optional<Allocation> bestFit(List<Allocation> fleet, int seats, boolean empty) {
        return fleet.stream().filter(v -> sharedRoom(v, seats) && (!empty || v.loads().isEmpty()))
                .min(Comparator.comparingInt(v -> v.slot().capacity() - v.seats()));
    }

    private static boolean sharedRoom(Allocation vehicle, int seats) {
        return vehicle.slot().privateBookingId() == null && vehicle.seats() + seats <= vehicle.slot().capacity();
    }

    private static List<List<BookingLoad>> clusters(List<BookingLoad> bookings, double radiusKm) {
        List<List<BookingLoad>> clusters = new ArrayList<>();
        var ordered = bookings.stream().filter(b -> !b.privateVehicle())
                .sorted(Comparator.comparingInt(BookingLoad::seats).reversed().thenComparingLong(BookingLoad::bookingId)).toList();
        for (BookingLoad booking : ordered) {
            var nearest = clusters.stream().filter(cluster -> distance(cluster, booking) <= radiusKm)
                    .min(Comparator.comparingDouble(cluster -> distance(cluster, booking)));
            if (nearest.isPresent()) nearest.get().add(booking);
            else clusters.add(new ArrayList<>(List.of(booking)));
        }
        clusters.sort(Comparator.comparingInt((List<BookingLoad> cluster) -> cluster.stream().mapToInt(BookingLoad::seats).sum()).reversed());
        return clusters;
    }

    private static double distance(List<BookingLoad> cluster, BookingLoad booking) {
        if (booking.destinations().isEmpty() || cluster.stream().anyMatch(b -> b.destinations().isEmpty())) {
            return Double.POSITIVE_INFINITY;
        }
        return cluster.stream().flatMap(b -> b.destinations().stream())
                .flatMapToDouble(a -> booking.destinations().stream().mapToDouble(b -> TripGroupingPolicy.straightKm(a, b)))
                .max().orElseThrow();
    }
}
