package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.policy.DispatchPolicy.Interval;
import com.example.racehorse_transport.policy.TripSchedulePolicy.Rules;
import com.example.racehorse_transport.policy.TripSchedulePolicy.StopTime;
import com.example.racehorse_transport.policy.TripSchedulePolicy.Window;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TripSchedulePolicyTest {

    @org.junit.jupiter.api.Test
    void requiresANetworkRestWhenTheHorsePassesFourContinuousHours() {
        var rules = new TripSchedulePolicy.Rules(4, 30, 10, 20, 10, 8, 10, 4);
        var start = java.time.Instant.parse("2026-12-10T01:00:00Z");
        var unsplit = TripSchedulePolicy.schedule(start, java.util.List.of(1, 1),
                java.util.List.of(6L * 3600), rules);
        org.assertj.core.api.Assertions.assertThat(TripSchedulePolicy.firstRestGap(0, 1, unsplit, java.util.Set.of(), rules))
                .isEqualTo(new TripSchedulePolicy.RestGap(0, 1, 4L * 3600));
        var rests = java.util.Set.of(1);
        var split = TripSchedulePolicy.schedule(start, java.util.List.of(1, 0, 1),
                java.util.List.of(3L * 3600, 3L * 3600), rests, rules);
        org.assertj.core.api.Assertions.assertThat(TripSchedulePolicy.firstRestGap(0, 2, split, rests, rules)).isNull();
        org.assertj.core.api.Assertions.assertThat(split.getLast().arrival()).isEqualTo(start.plusSeconds(23_400));
    }

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Rules RULES = new Rules(4, 30, 10, 20, 10);
    private static final Window DAY = new Window(6, 18);
    private static final long HOUR = 3600;

    private static Instant at(String local) {
        return java.time.LocalDateTime.parse(local).atZone(ZONE).toInstant();
    }

    @Test
    void dwellsByNumberOfHorsesAndNotWhereNothingHappens() {
        List<StopTime> times = TripSchedulePolicy.schedule(at("2027-01-01T06:00"), List.of(0, 2, 1, 3), List.of(HOUR, HOUR, HOUR), RULES);
        assertThat(times.get(0).arrival()).isEqualTo(at("2027-01-01T06:00"));
        assertThat(times.get(1)).isEqualTo(new StopTime(at("2027-01-01T07:00"), at("2027-01-01T07:40"), 0));
        assertThat(times.get(2)).isEqualTo(new StopTime(at("2027-01-01T08:40"), at("2027-01-01T09:10"), 0));
        assertThat(times.get(3)).isEqualTo(new StopTime(at("2027-01-01T10:10"), at("2027-01-01T11:00"), 0));
    }

    @Test
    void restsWhenDrivingLongerThanTheLimitAndLongStopsCountAsRest() {
        assertThat(TripSchedulePolicy.schedule(at("2027-01-01T06:00"), List.of(0, 1), List.of(4 * HOUR), RULES).get(1).restMinutes()).isZero();
        StopTime five = TripSchedulePolicy.schedule(at("2027-01-01T06:00"), List.of(0, 1), List.of(5 * HOUR), RULES).get(1);
        assertThat(five.restMinutes()).isEqualTo(30);
        assertThat(five.arrival()).isEqualTo(at("2027-01-01T11:30"));
        // Dừng 30 phút cho một con là đủ nghỉ nên đoạn sau không phải nghỉ thêm
        assertThat(TripSchedulePolicy.schedule(at("2027-01-01T06:00"), List.of(0, 1, 1), List.of(3 * HOUR, 3 * HOUR), RULES).get(2).restMinutes()).isZero();
        // Điểm dừng quá ngắn thì giờ lái cộng dồn
        Rules shortStops = new Rules(4, 30, 10, 5, 5);
        assertThat(TripSchedulePolicy.schedule(at("2027-01-01T06:00"), List.of(0, 1, 1), List.of(3 * HOUR, 3 * HOUR), shortStops).get(2).restMinutes()).isEqualTo(30);
    }

    @Test
    void pickupReachesTheFirstAddressWhenTheWindowOpensOnTheChosenDay() {
        Instant start = TripSchedulePolicy.pickupDeparture(LocalDate.of(2027, 1, 1), DAY, ZONE, List.of(2 * HOUR, HOUR), RULES);
        assertThat(start).isEqualTo(at("2027-01-01T04:00"));
        assertThat(TripSchedulePolicy.schedule(start, List.of(0, 1, 1), List.of(2 * HOUR, HOUR), RULES).get(1).arrival()).isEqualTo(at("2027-01-01T06:00"));
    }

    @Test
    void deliveryWaitsForTheNextWindowWhenTheLinehaulArrivesLate() {
        List<Integer> horses = List.of(1, 1, 0);
        List<Long> drives = List.of(HOUR, HOUR);
        assertThat(TripSchedulePolicy.deliveryDeparture(at("2027-01-01T10:00"), DAY, ZONE, horses, drives, RULES)).isEqualTo(at("2027-01-01T10:00"));
        assertThat(TripSchedulePolicy.deliveryDeparture(at("2027-01-01T19:00"), DAY, ZONE, horses, drives, RULES)).isEqualTo(at("2027-01-02T05:00"));
        assertThat(TripSchedulePolicy.deliveryDeparture(at("2027-01-01T02:00"), DAY, ZONE, horses, drives, RULES)).isEqualTo(at("2027-01-01T05:00"));
        // Điểm giao cuối quá 18:00 thì dời cả lượt sang hôm sau
        assertThat(TripSchedulePolicy.deliveryDeparture(at("2027-01-01T16:00"), DAY, ZONE, List.of(2, 1, 1, 0), List.of(HOUR, 2 * HOUR, HOUR), RULES))
                .isEqualTo(at("2027-01-02T05:00"));
    }

    @Test
    void limitsTheRideOfEachHorse() {
        List<Long> drives = List.of(HOUR, 2 * HOUR, 3 * HOUR);
        assertThat(TripSchedulePolicy.rideSeconds(drives, 1, 3)).isEqualTo(5 * HOUR);
        assertThat(TripSchedulePolicy.rideLimitSeconds(false, RULES)).isEqualTo(5 * HOUR);
        assertThat(TripSchedulePolicy.rideLimitSeconds(true, RULES)).isEqualTo(10 * HOUR);
    }

    @Test
    void sumsDrivingPerDayAcrossTripsAndMidnight() {
        Map<LocalDate, Long> driving = TripSchedulePolicy.drivingByDay(List.of(
                List.of(interval("2027-01-01T06:00"), interval("2027-01-01T09:00")),
                List.of(interval("2027-01-01T22:00"), interval("2027-01-02T01:00"))), ZONE, RULES);
        assertThat(driving).containsExactly(Map.entry(LocalDate.of(2027, 1, 1), 5 * HOUR), Map.entry(LocalDate.of(2027, 1, 2), HOUR));
        // Khoảng 4 giờ 30 gồm 30 phút nghỉ
        assertThat(TripSchedulePolicy.drivingByDay(List.of(List.of(interval("2027-01-01T06:00"), interval("2027-01-01T10:30"))), ZONE, RULES))
                .containsEntry(LocalDate.of(2027, 1, 1), 4 * HOUR);
    }

    @Test
    void rejectsMoreThanTheDailyLimit() {
        assertThatCode(() -> TripSchedulePolicy.requireDailyDriving("staffId", "Tài xế A", Map.of(LocalDate.of(2027, 1, 1), 10 * HOUR), RULES)).doesNotThrowAnyException();
        assertThatThrownBy(() -> TripSchedulePolicy.requireDailyDriving("staffId", "Tài xế A", Map.of(LocalDate.of(2027, 1, 1), 11 * HOUR), RULES))
                .isInstanceOf(DispatchRuleException.class).hasMessageContaining("11,0 giờ").hasMessageContaining("1/1");
    }

    private static Interval interval(String local) {
        return new Interval(at(local), at(local));
    }
}
