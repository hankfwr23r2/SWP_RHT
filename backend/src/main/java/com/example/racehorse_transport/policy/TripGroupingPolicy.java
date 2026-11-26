package com.example.racehorse_transport.policy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// Gợi ý gom đơn vào một lượt đón hoặc giao (NGHIEP_VU GĐ5, THONG_BAO_LUONG_B B1), hàm thuần.
// Lượt đón: xe đi qua các địa chỉ theo thứ tự rồi về hub. Lượt giao là chiều ngược lại (hub rồi các địa chỉ theo thứ tự ngược).
// Một nhóm hợp lệ khi: đủ chỗ; mỗi đơn đi vòng không quá DETOUR_LIMIT lần so với đi thẳng giữa địa chỉ đó và hub;
// con lên đầu tiên ở trên xe không quá maxHours. Điều phối duyệt rồi mới lập lượt xe.
public final class TripGroupingPolicy {

    public static final double DETOUR_LIMIT = 1.5;
    // Đường bộ dài hơn đường chim bay; ước lượng để gợi ý nhanh, không gọi dịch vụ dẫn đường
    static final double ROAD_FACTOR = 1.3;
    private static final double EARTH_RADIUS_KM = 6371.0;

    private TripGroupingPolicy() {}

    public record Point(double lat, double lng) {}

    public record Candidate(Long bookingId, Point point, int seats, boolean privateVehicle) {
        public Candidate(Long bookingId, Point point, int seats) {
            this(bookingId, point, seats, false);
        }
    }

    // order: thứ tự đón (lượt giao thì nơi gọi đảo ngược). routeKm: từ địa chỉ đầu tới hub. hours: thời gian ngựa đầu tiên ở trên xe
    public record Group(List<Candidate> order, double routeKm, double hours) {}

    public static double km(Point a, Point b) {
        return straightKm(a, b) * ROAD_FACTOR;
    }

    public static double straightKm(Point a, Point b) {
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLng = Math.toRadians(b.lng() - a.lng());
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(a.lat())) * Math.cos(Math.toRadians(b.lat())) * Math.pow(Math.sin(dLng / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(Math.min(1, h)));
    }

    // Đơn xa hub nhất làm gốc, chèn thêm đơn gần nó vào chỗ tốn ít đường nhất nếu nhóm vẫn hợp lệ.
    // Đơn đi một mình luôn là một nhóm (kể cả khi tự nó đã quá maxHours: điều phối thấy số giờ và tự xử lý)
    public static List<Group> group(Point hub, List<Candidate> candidates, int capacity, double maxHours, double speedKmh) {
        return group(hub, candidates, capacity, maxHours, speedKmh, Double.POSITIVE_INFINITY);
    }

    public static List<Group> group(Point hub, List<Candidate> candidates, int capacity, double maxHours, double speedKmh, double radiusKm) {
        List<Candidate> remaining = new ArrayList<>(candidates.stream()
                .sorted(Comparator.comparingDouble((Candidate c) -> km(c.point(), hub)).reversed().thenComparing(Candidate::bookingId)).toList());
        List<Group> groups = new ArrayList<>();
        while (!remaining.isEmpty()) {
            Candidate seed = remaining.removeFirst();
            List<Candidate> route = new ArrayList<>(List.of(seed));
            boolean added = true;
            while (added) {
                added = false;
                List<Candidate> nearby = remaining.stream().sorted(Comparator.comparingDouble(c -> km(c.point(), seed.point()))).toList();
                for (Candidate candidate : nearby) {
                    if (route.stream().anyMatch(stop -> straightKm(stop.point(), candidate.point()) > radiusKm)) continue;
                    List<Candidate> best = bestInsertion(hub, route, candidate, capacity, maxHours, speedKmh);
                    if (best != null) {
                        route = best;
                        remaining.remove(candidate);
                        added = true;
                        break;
                    }
                }
            }
            groups.add(new Group(List.copyOf(route), routeKm(hub, route), routeKm(hub, route) / speedKmh));
        }
        return groups;
    }

    // Quãng đường từ địa chỉ thứ index tới hub theo thứ tự đón
    static double kmToHub(Point hub, List<Candidate> route, int index) {
        double total = 0;
        for (int i = index; i < route.size() - 1; i++) {
            total += km(route.get(i).point(), route.get(i + 1).point());
        }
        return total + km(route.getLast().point(), hub);
    }

    private static double routeKm(Point hub, List<Candidate> route) {
        return kmToHub(hub, route, 0);
    }

    private static List<Candidate> bestInsertion(Point hub, List<Candidate> route, Candidate candidate, int capacity, double maxHours, double speedKmh) {
        if (candidate.privateVehicle() || route.getFirst().privateVehicle()) return null;
        if (route.stream().mapToInt(Candidate::seats).sum() + candidate.seats() > capacity) return null;
        List<Candidate> best = null;
        double bestKm = Double.MAX_VALUE;
        for (int at = 0; at <= route.size(); at++) {
            List<Candidate> trial = new ArrayList<>(route);
            trial.add(at, candidate);
            if (!valid(hub, trial, maxHours, speedKmh)) continue;
            double total = routeKm(hub, trial);
            if (total < bestKm) {
                bestKm = total;
                best = trial;
            }
        }
        return best;
    }

    private static boolean valid(Point hub, List<Candidate> route, double maxHours, double speedKmh) {
        for (int i = 0; i < route.size(); i++) {
            double direct = km(route.get(i).point(), hub);
            if (kmToHub(hub, route, i) > DETOUR_LIMIT * direct + 1e-9) return false;
        }
        return routeKm(hub, route) / speedKmh <= maxHours + 1e-9;
    }
}
