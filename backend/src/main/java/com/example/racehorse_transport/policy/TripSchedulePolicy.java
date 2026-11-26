package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.policy.DispatchPolicy.Interval;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

// Tính giờ lượt đón, giao, chạy thẳng từ thời gian lái từng đoạn (người dùng duyệt 24/11/2026), hàm thuần.
// Nghỉ bắt buộc cộng vào thời gian đi đường; dừng nhận, giao đủ lâu thì tính là nghỉ.
public final class TripSchedulePolicy {

    public record Rules(int maxContinuousHours, int minRestMinutes, int maxDriveHoursPerDay, int stopBaseMinutes, int stopMinutesPerHorse,
                        int maxTransferHorseHours, int maxDirectHorseHours, int horseRestIntervalHours) {
        public Rules(int maxContinuousHours, int minRestMinutes, int maxDriveHoursPerDay, int stopBaseMinutes, int stopMinutesPerHorse) {
            this(maxContinuousHours, minRestMinutes, maxDriveHoursPerDay, stopBaseMinutes, stopMinutesPerHorse, 0, 0, 0);
        }
    }

    public record RestGap(int fromStop, int toStop, long secondsAvailable) {}

    // Khung giờ trong ngày, giờ kết thúc 24 nghĩa là hết ngày
    public record Window(int startHour, int endHour) {}

    // restMinutes: nghỉ dọc đường trên đoạn tới điểm này
    public record StopTime(Instant arrival, Instant departure, int restMinutes) {}

    private TripSchedulePolicy() {}

    public static Duration dwell(int horses, Rules rules) {
        return horses == 0 ? Duration.ZERO : Duration.ofMinutes(rules.stopBaseMinutes() + (long) rules.stopMinutesPerHorse() * horses);
    }

    // horses: số ngựa lên hoặc xuống ở từng điểm. driveSeconds: thời gian lái từng đoạn. firstDeparture: giờ rời điểm đầu
    public static List<StopTime> schedule(Instant firstDeparture, List<Integer> horses, List<Long> driveSeconds, Rules rules) {
        return schedule(firstDeparture, horses, driveSeconds, Set.of(), rules);
    }

    public static List<StopTime> schedule(Instant firstDeparture, List<Integer> horses, List<Long> driveSeconds,
                                          Set<Integer> restStops, Rules rules) {
        if (horses.size() != driveSeconds.size() + 1) {
            throw new IllegalArgumentException("Số điểm dừng phải bằng số đoạn cộng một.");
        }
        long maxContinuous = maxContinuousSeconds(rules);
        List<StopTime> result = new ArrayList<>();
        result.add(new StopTime(firstDeparture.minus(dwell(horses.getFirst(), rules)), firstDeparture, 0));
        Instant leave = firstDeparture;
        long carried = 0;
        for (int i = 0; i < driveSeconds.size(); i++) {
            long total = carried + driveSeconds.get(i);
            long rests = restsNeeded(total, maxContinuous);
            int restMinutes = Math.toIntExact(rests * rules.minRestMinutes());
            Instant arrival = leave.plusSeconds(driveSeconds.get(i)).plus(Duration.ofMinutes(restMinutes));
            Duration dwell = restStops.contains(i + 1) ? Duration.ofMinutes(rules.minRestMinutes()) : dwell(horses.get(i + 1), rules);
            leave = arrival.plus(dwell);
            carried = dwell.toMinutes() >= rules.minRestMinutes() ? 0 : total - rests * maxContinuous;
            result.add(new StopTime(arrival, leave, restMinutes));
        }
        return result;
    }

    // Giờ rời điểm đầu để tới điểm thứ hai đúng giờ target
    public static Instant departureToReachFirstStop(Instant target, List<Long> driveSeconds, Rules rules) {
        long drive = driveSeconds.getFirst();
        long rest = restsNeeded(drive, maxContinuousSeconds(rules)) * rules.minRestMinutes() * 60L;
        return target.minusSeconds(drive + rest);
    }

    // Lượt đón, chạy thẳng: tới địa chỉ đầu lúc mở khung giờ của ngày khách chọn
    public static Instant pickupDeparture(LocalDate pickupDate, Window window, ZoneId zone, List<Long> driveSeconds, Rules rules) {
        return departureToReachFirstStop(pickupDate.atTime(window.startHour(), 0).atZone(zone).toInstant(), driveSeconds, rules);
    }

    // Lượt giao: rời hub khi ngựa sẵn sàng. Điểm giao nào rơi ngoài khung giờ thì dời cả lượt sang đầu khung giờ kế tiếp
    public static Instant deliveryDeparture(Instant ready, Window window, ZoneId zone, List<Integer> horses, List<Long> driveSeconds, Rules rules) {
        return deliveryDeparture(ready, window, zone, horses, driveSeconds, Set.of(), rules);
    }

    public static Instant deliveryDeparture(Instant ready, Window window, ZoneId zone, List<Integer> horses,
                                            List<Long> driveSeconds, Set<Integer> restStops, Rules rules) {
        List<StopTime> now = schedule(ready, horses, driveSeconds, restStops, rules);
        int firstAddress = 1;
        int lastAddress = now.size() - 2;
        while (restStops.contains(firstAddress)) firstAddress++;
        while (restStops.contains(lastAddress)) lastAddress--;
        Instant firstArrival = now.get(firstAddress).arrival();
        if (inWindow(firstArrival, window, zone) && inWindow(now.get(lastAddress).arrival(), window, zone)) {
            return ready;
        }
        LocalDate day = LocalDate.ofInstant(firstArrival, zone);
        Instant opening = day.atTime(window.startHour(), 0).atZone(zone).toInstant();
        if (!opening.isAfter(firstArrival)) {
            opening = day.plusDays(1).atTime(window.startHour(), 0).atZone(zone).toInstant();
        }
        return opening.minus(Duration.between(ready, firstArrival));
    }

    public static boolean inWindow(Instant at, Window window, ZoneId zone) {
        int minutes = at.atZone(zone).toLocalTime().toSecondOfDay() / 60;
        return minutes >= window.startHour() * 60 && minutes <= window.endHour() * 60;
    }

    // Thời gian lái của con ngựa lên ở board, xuống ở alight
    public static long rideSeconds(List<Long> driveSeconds, int board, int alight) {
        return driveSeconds.subList(board, alight).stream().mapToLong(Long::longValue).sum();
    }

    // Quy tắc #31: đón, giao một chiều không quá nửa giới hạn lái mỗi ngày; chạy thẳng không quá giới hạn ngày
    public static long rideLimitSeconds(boolean direct, Rules rules) {
        int configured = direct ? rules.maxDirectHorseHours() : rules.maxTransferHorseHours();
        if (configured > 0) return Duration.ofHours(configured).getSeconds();
        long day = rules.maxDriveHoursPerDay() * 3600L;
        return direct ? day : day / 2;
    }

    public static RestGap firstRestGap(int board, int alight, List<StopTime> times, Set<Integer> restStops, Rules rules) {
        long limit = Duration.ofHours(rules.horseRestIntervalHours()).getSeconds();
        Instant lastRestEnd = times.get(board).departure();
        for (int to = board + 1; to <= alight; to++) {
            long elapsed = Duration.between(lastRestEnd, times.get(to).arrival()).getSeconds();
            if (elapsed > limit) {
                long available = limit - Duration.between(lastRestEnd, times.get(to - 1).departure()).getSeconds();
                return new RestGap(to - 1, to, available);
            }
            if (restStops.contains(to)) lastRestEnd = times.get(to).departure();
        }
        return null;
    }

    public static void requirePlannedRideWithinLimit(Instant board, Instant alight, boolean direct, Rules rules) {
        long limit = rideLimitSeconds(direct, rules);
        if (Duration.between(board, alight).compareTo(Duration.ofSeconds(limit)) > 0) {
            String limitHours = hours(limit).replaceFirst(",0$", "");
            throw new DispatchRuleException("alightTripStopId", "Ngựa ngồi xe quá " + limitHours
                    + " giờ theo kế hoạch. Bỏ bớt điểm hoặc tách lượt.", true);
        }
    }

    public static void requireMultiPlaceDrivingLimit(List<Interval> stops, Rules rules) {
        long driving = drivingByDay(List.of(stops), ZoneId.of("Asia/Ho_Chi_Minh"), rules).values()
                .stream().mapToLong(Long::longValue).sum();
        if (driving > Duration.ofHours(rules.maxDriveHoursPerDay()).getSeconds()) {
            throw new DispatchRuleException("places", "Lượt giao và quay về vượt giới hạn giờ lái.", true);
        }
    }

    // Giờ lái mỗi ngày của một lượt xe theo kế hoạch: khoảng giữa hai điểm dừng trừ giờ nghỉ bắt buộc, qua nửa đêm thì chia theo ngày
    public static Map<LocalDate, Long> drivingByDay(Collection<List<Interval>> trips, ZoneId zone, Rules rules) {
        Map<LocalDate, Long> result = new TreeMap<>();
        for (List<Interval> stops : trips) {
            for (int i = 1; i < stops.size(); i++) {
                addDriving(result, stops.get(i - 1).end(), stops.get(i).start(), zone, rules);
            }
        }
        return result;
    }

    public static void requireDailyDriving(String field, String subject, Map<LocalDate, Long> drivingByDay, Rules rules) {
        long limit = rules.maxDriveHoursPerDay() * 3600L;
        drivingByDay.forEach((day, seconds) -> {
            if (seconds > limit) {
                throw new DispatchRuleException(field, subject + " sẽ lái " + hours(seconds) + " giờ trong ngày " + day.getDayOfMonth() + "/"
                        + day.getMonthValue() + ", quá giới hạn " + rules.maxDriveHoursPerDay() + " giờ.", true);
            }
        });
    }

    private static void addDriving(Map<LocalDate, Long> result, Instant from, Instant to, ZoneId zone, Rules rules) {
        long gap = Duration.between(from, to).getSeconds();
        if (gap <= 0) {
            return;
        }
        long rest = rules.minRestMinutes() * 60L;
        long drive = gap - gap / (maxContinuousSeconds(rules) + rest) * rest;
        Instant start = from;
        while (start.isBefore(to)) {
            LocalDate day = LocalDate.ofInstant(start, zone);
            Instant midnight = day.plusDays(1).atStartOfDay(zone).toInstant();
            Instant end = midnight.isBefore(to) ? midnight : to;
            result.merge(day, Math.round((double) drive * Duration.between(start, end).getSeconds() / gap), Long::sum);
            start = end;
        }
    }

    private static long restsNeeded(long driveSeconds, long maxContinuous) {
        return Math.max(0, (driveSeconds + maxContinuous - 1) / maxContinuous - 1);
    }

    private static long maxContinuousSeconds(Rules rules) {
        return rules.maxContinuousHours() * 3600L;
    }

    private static String hours(long seconds) {
        return String.valueOf(Math.round(seconds / 360.0) / 10.0).replace('.', ',');
    }
}
