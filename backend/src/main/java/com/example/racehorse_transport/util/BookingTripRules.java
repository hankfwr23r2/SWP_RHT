package com.example.racehorse_transport.util;

import com.example.racehorse_transport.dto.booking.BookingDetailsPayloadDto;
import com.example.racehorse_transport.dto.booking.BookingHorseDto;
import com.example.racehorse_transport.dto.coordinator.VehicleTripDto;
import com.example.racehorse_transport.entity.Booking;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BookingTripRules {
    private BookingTripRules() {}

    // Ngựa bị loại ở điểm đón: ốm/sốt, hoặc chip không khớp hồ sơ
    private static final Set<String> EXCLUDED_HORSE_STATUSES = Set.of("UNFIT", "CHIP_MISMATCH");

    public static boolean matchesStaffIdentifier(String assigned, String staffCode, String staffId, String username, String name, String role) {
        String normalized = canonicalStaffIdentifier(assigned, role);
        return normalized.equals(canonicalStaffIdentifier(staffCode, role))
                || normalized.equals(canonicalStaffIdentifier(staffId, role))
                || normalized.equals(canonicalStaffIdentifier(username, role))
                || (!name.isBlank() && normalized.equals(canonicalStaffIdentifier(name, role)));
    }

    private static String canonicalStaffIdentifier(String value, String role) {
        if (value == null) return "";
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        if ("DRIVER".equalsIgnoreCase(role) && normalized.matches("^TX-\\d+$")) {
            return "DR-" + normalized.substring(3);
        }
        if ("ESCORT".equalsIgnoreCase(role) && normalized.matches("^(NV|HT)-\\d+$")) {
            return "ES-" + normalized.substring(normalized.indexOf('-') + 1);
        }
        return normalized;
    }

    public static String getMedicalStatus(BookingDetailsPayloadDto payload) {
        if (payload == null || payload.getMedical() == null) {
            return "";
        }
        if (payload.getMedical() instanceof Map<?, ?> map) {
            Object status = map.get("status");
            return status != null ? String.valueOf(status) : "";
        }
        return "";
    }

    // Ngựa còn đi trên chuyến (đã bỏ các con bị loại ở điểm đón)
    public static List<BookingHorseDto> horsesOnTrip(BookingDetailsPayloadDto payload, VehicleTripDto trip) {
        Set<String> tripHorseIds = new HashSet<>(trip.getHorseIds() != null ? trip.getHorseIds() : List.of());
        if (payload.getHorses() == null) return List.of();
        return payload.getHorses().stream()
                .filter(h -> h.getHorseId() != null && tripHorseIds.contains(h.getHorseId()))
                .filter(h -> h.getStatus() == null || !EXCLUDED_HORSE_STATUSES.contains(h.getStatus().toUpperCase(java.util.Locale.ROOT)))
                .toList();
    }

    public static Map<String, Object> pickupCheckpoint(VehicleTripDto trip) {
        Map<String, Object> run = trip.getRun();
        List<Map<String, Object>> checkpoints = run == null ? List.of() : checkpointMaps(run.get("checkpoints"));
        return checkpoints.stream()
                .filter(cp -> "pickup".equals(cp.get("type")) || "pickup".equals(cp.get("id")))
                .findFirst()
                .orElse(null);
    }

    // Việc còn thiếu để nhận ngựa ở điểm đón (null là đã xong). Xong hết mới thu 70% còn lại và cho xe xuất phát.
    public static String missingPickupStep(BookingDetailsPayloadDto payload, VehicleTripDto trip) {
        if (trip.getDepartedAt() == null) return "Xe cần xuất phát đến điểm đón trước khi bắt đầu vận chuyển.";
        Map<String, Object> pickup = pickupCheckpoint(trip);
        if (pickup == null) return "Tài xế cần xác nhận đã đến điểm đón trước khi xuất phát.";
        if (pickup.get("arrivedAt") == null || isBlank(pickup.get("photo"))) {
            return "Cần xác nhận điểm đón kèm ảnh trước khi xuất phát.";
        }
        List<BookingHorseDto> horses = horsesOnTrip(payload, trip);
        if (horses.isEmpty()) return "Chuyến không còn ngựa nào để chở, không thể xuất phát.";
        Set<String> scanned = new HashSet<>();
        for (String chip : stringList(pickup.get("chips"))) scanned.add(chip.toUpperCase());
        for (BookingHorseDto horse : horses) {
            if (isBlank(horse.getMicrochip()) || !scanned.contains(horse.getMicrochip().toUpperCase())) {
                return "Cần quét microchip của tất cả ngựa đủ điều kiện trên chuyến trước khi xuất phát.";
            }
        }
        List<String> originals = stringList(pickup.get("originals"));
        if (!originals.containsAll(List.of(
                "Hộ chiếu ngựa bản gốc (FEI / National Passport)",
                "Sổ tiêm phòng",
                "Phiếu xét nghiệm EIA/EVA, bản gốc kèm 02 bản sao công chứng"))) {
            return "Cần xác nhận đã nhận đủ hồ sơ gốc của ngựa trước khi xuất phát.";
        }
        if (isBlank(pickup.get("handoverPhoto"))) return "Cần ảnh biên bản giao nhận đã ký trước khi xuất phát.";
        return null;
    }

    // Mọi xe còn chở ngựa đã làm xong thủ tục nhận ngựa: lúc này khách mới thanh toán 70% còn lại
    public static boolean pickupComplete(BookingDetailsPayloadDto payload) {
        if (payload.getTrips() == null) return false;
        List<VehicleTripDto> withHorses = payload.getTrips().stream()
                .filter(t -> !horsesOnTrip(payload, t).isEmpty())
                .toList();
        return !withHorses.isEmpty() && withHorses.stream().allMatch(t -> missingPickupStep(payload, t) == null);
    }

    public static List<Map<String, Object>> ensureRouteCheckpoints(Booking booking, BookingDetailsPayloadDto payload, VehicleTripDto trip) {
        Map<String, Object> run = trip.getRun() != null ? trip.getRun() : new HashMap<>();
        List<Map<String, Object>> existing = checkpointMaps(run.get("checkpoints"));
        // Sau khi SOS được duyệt, danh sách mốc của chuyến là kế hoạch đang có hiệu lực.
        // Không dựng lại từ tuyến gốc vì sẽ mất mốc SOS và khôi phục mốc đã được thay thế.
        if (trip.getIncidentRoute() != null && run.get("startedAt") != null
                && existing.stream().anyMatch(cp -> "delivery".equals(cp.get("type")))) {
            return existing;
        }
        Map<String, Map<String, Object>> byId = new HashMap<>();
        for (Map<String, Object> cp : existing) {
            if (cp.get("id") != null) byId.put(String.valueOf(cp.get("id")), cp);
        }

        Object routeValue = payload.getRoute();
        Map<String, Object> route = objectMap(routeValue);
        Map<String, Object> pickup = byId.getOrDefault("pickup", new HashMap<>());
        pickup.put("id", "pickup");
        pickup.put("type", "pickup");
        pickup.putIfAbsent("label", "Nhận ngựa tại điểm đón");
        pickup.putIfAbsent("place", booking.getPickupAddress() != null ? booking.getPickupAddress() : "Điểm đón");
        if (run.get("startedAt") != null && pickup.get("arrivedAt") != null) {
            pickup.putIfAbsent("doneAt", run.get("startedAt"));
        }

        List<Map<String, Object>> ordered = new ArrayList<>();
        ordered.add(pickup);
        List<Map<String, Object>> middle = new ArrayList<>();
        List<Map<String, Object>> legs = route == null ? List.of() : mapList(route.get("legs"));
        List<Map<String, Object>> rests = route == null ? List.of() : mapList(route.get("rests"));
        for (int i = 0; i < rests.size(); i++) {
            Map<String, Object> rest = rests.get(i);
            String id = "rest-" + (i + 1);
            Map<String, Object> cp = byId.getOrDefault(id, new HashMap<>());
            cp.put("id", id);
            cp.put("type", "rest");
            cp.putIfAbsent("label", "Trạm nghỉ " + (i + 1));
            cp.putIfAbsent("place", String.valueOf(rest.getOrDefault("name", "Trạm nghỉ " + (i + 1))));
            long plannedAt = legs.isEmpty() ? System.currentTimeMillis()
                    : number(legs.get(Math.min(i, legs.size() - 1)).get("arriveAt"), System.currentTimeMillis());
            cp.putIfAbsent("plannedAt", plannedAt);
            middle.add(cp);
        }

        long borderEta = route == null ? 0L : number(route.get("borderEta"), 0L);
        if ("international".equalsIgnoreCase(booking.getBookingType()) && borderEta > 0) {
            String gate = payload.getGate() != null && !payload.getGate().isBlank() ? payload.getGate() : "Cửa khẩu";
            Map<String, Object> border = byId.getOrDefault("border", new HashMap<>());
            border.put("id", "border"); border.put("type", "border");
            border.putIfAbsent("label", "Tới cửa khẩu"); border.putIfAbsent("place", gate); border.putIfAbsent("plannedAt", borderEta);
            middle.add(border);
            Map<String, Object> customs = byId.getOrDefault("customs", new HashMap<>());
            customs.put("id", "customs"); customs.put("type", "customs");
            customs.putIfAbsent("label", "Hoàn tất thông quan"); customs.putIfAbsent("place", gate); customs.putIfAbsent("plannedAt", borderEta + 90 * 60_000L);
            middle.add(customs);
        }
        middle.sort((a, b) -> {
            int byTime = Long.compare(number(a.get("plannedAt"), 0L), number(b.get("plannedAt"), 0L));
            if (byTime != 0) return byTime;
            return "border".equals(a.get("type")) ? -1 : "border".equals(b.get("type")) ? 1 : 0;
        });
        ordered.addAll(middle);

        Map<String, Object> delivery = byId.getOrDefault("delivery", new HashMap<>());
        delivery.put("id", "delivery"); delivery.put("type", "delivery");
        delivery.putIfAbsent("label", "Bàn giao tại điểm giao");
        delivery.putIfAbsent("place", booking.getDropoffAddress() != null ? booking.getDropoffAddress() : "Điểm giao");
        long lastArrival = legs.isEmpty() ? System.currentTimeMillis() : number(legs.get(legs.size() - 1).get("arriveAt"), System.currentTimeMillis());
        long lastPlanned = middle.isEmpty() ? 0L : number(middle.get(middle.size() - 1).get("plannedAt"), 0L);
        delivery.putIfAbsent("plannedAt", Math.max(lastArrival, lastPlanned + (middle.isEmpty() ? 0L : 30 * 60_000L)));
        ordered.add(delivery);
        return ordered;
    }

    public static List<Map<String, Object>> checkpointMaps(Object value) {
        return mapList(value);
    }

    public static List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) return new ArrayList<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> map = objectMap(item);
            if (map != null) result.add(map);
        }
        return result;
    }

    private static Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) return null;
        Map<String, Object> result = new HashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    public static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(item -> item != null).map(String::valueOf).toList();
    }

    public static boolean isBlank(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }

    public static long number(Object value, long fallback) {
        if (value instanceof Number n) return n.longValue();
        try { return value == null ? fallback : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return fallback; }
    }


}
