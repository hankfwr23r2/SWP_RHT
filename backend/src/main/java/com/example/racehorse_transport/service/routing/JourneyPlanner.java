package com.example.racehorse_transport.service.routing;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

// Từ thời gian lái thật của từng chặng → lịch trình thực tế: nghỉ bắt buộc, nghỉ đêm, chờ cửa khẩu mở và làm thủ tục.
// Hàm thuần, không đọc DB: tham số quy định truyền vào qua Rules.
public final class JourneyPlanner {

    public enum StopKind { PICKUP, VIA, GATE, DROPOFF }

    public enum EventType { DRIVE, REST, ROADSIDE_REST, OVERNIGHT, BORDER_WAIT, BORDER_PROCESSING }

    public record Rules(double maxContinuousHours, int minRestMinutes, double maxDriveHoursPerDay,
                        double overnightRestHours, double borderProcessingHours) {}

    public record Stop(String name, StopKind kind, Integer openingMinutes, Integer closingMinutes) {}

    public record Leg(double km, long driveSeconds) {}

    // place == null: nghỉ dọc đường, vị trí là legIndex + kmIntoLeg (nơi gọi đặt tên theo trạm nghỉ gần nhất)
    public record Event(EventType type, String place, Instant startAt, Instant endAt, int legIndex, double kmIntoLeg) {}

    public record Journey(List<Event> events, Instant arrivalAt, Instant borderArrivalAt, int crewDays) {}

    private JourneyPlanner() {}

    public static Journey plan(Instant departAt, List<Stop> stops, List<Leg> legs, Rules rules, ZoneId zone) {
        if (stops.size() != legs.size() + 1) {
            throw new IllegalArgumentException("Số điểm dừng phải bằng số chặng cộng một.");
        }
        long maxContinuous = Math.round(rules.maxContinuousHours() * 3600);
        long maxPerDay = Math.round(rules.maxDriveHoursPerDay() * 3600);
        long rest = rules.minRestMinutes() * 60L;
        long overnight = Math.round(rules.overnightRestHours() * 3600);
        long processing = Math.round(rules.borderProcessingHours() * 3600);

        List<Event> events = new ArrayList<>();
        Instant t = departAt;
        Instant borderArrival = null;
        long continuous = 0;
        long today = 0;
        for (int i = 0; i < legs.size(); i++) {
            Leg leg = legs.get(i);
            long remaining = leg.driveSeconds();
            long driven = 0;
            while (remaining > 0) {
                double km = kmAt(leg, driven);
                String here = driven == 0 ? stops.get(i).name() : null;
                if (today >= maxPerDay) {
                    events.add(new Event(EventType.OVERNIGHT, here, t, t.plusSeconds(overnight), i, km));
                    t = t.plusSeconds(overnight);
                    continuous = 0;
                    today = 0;
                    continue;
                }
                if (continuous >= maxContinuous) {
                    events.add(new Event(EventType.ROADSIDE_REST, here, t, t.plusSeconds(rest), i, km));
                    t = t.plusSeconds(rest);
                    continuous = 0;
                    continue;
                }
                long chunk = Math.min(remaining, Math.min(maxContinuous - continuous, maxPerDay - today));
                events.add(new Event(EventType.DRIVE, null, t, t.plusSeconds(chunk), i, km));
                t = t.plusSeconds(chunk);
                remaining -= chunk;
                driven += chunk;
                continuous += chunk;
                today += chunk;
            }
            Stop next = stops.get(i + 1);
            if (next.kind() == StopKind.VIA) {
                events.add(new Event(EventType.REST, next.name(), t, t.plusSeconds(rest), i, leg.km()));
                t = t.plusSeconds(rest);
                continuous = 0;
            } else if (next.kind() == StopKind.GATE) {
                borderArrival = t;
                Instant open = nextOpening(t, next, zone);
                if (open.isAfter(t)) {
                    events.add(new Event(EventType.BORDER_WAIT, next.name(), t, open, i, leg.km()));
                    if (Duration.between(t, open).getSeconds() >= overnight) {
                        today = 0;
                    }
                    t = open;
                }
                events.add(new Event(EventType.BORDER_PROCESSING, next.name(), t, t.plusSeconds(processing), i, leg.km()));
                t = t.plusSeconds(processing);
                continuous = 0;
            }
        }
        int crewDays = (int) ChronoUnit.DAYS.between(departAt.atZone(zone).toLocalDate(), t.atZone(zone).toLocalDate()) + 1;
        return new Journey(List.copyOf(events), t, borderArrival, crewDays);
    }

    private static double kmAt(Leg leg, long drivenSeconds) {
        return leg.driveSeconds() == 0 ? 0 : leg.km() * drivenSeconds / leg.driveSeconds();
    }

    // Cửa khẩu chưa khai báo giờ thì coi như mở cả ngày
    private static Instant nextOpening(Instant at, Stop gate, ZoneId zone) {
        if (gate.openingMinutes() == null || gate.closingMinutes() == null) {
            return at;
        }
        ZonedDateTime local = at.atZone(zone);
        int minute = local.getHour() * 60 + local.getMinute();
        ZonedDateTime todayOpening = local.toLocalDate().atStartOfDay(zone).plusMinutes(gate.openingMinutes());
        if (minute < gate.openingMinutes()) {
            return todayOpening.toInstant();
        }
        if (minute >= gate.closingMinutes()) {
            return todayOpening.plusDays(1).toInstant();
        }
        return at;
    }
}
