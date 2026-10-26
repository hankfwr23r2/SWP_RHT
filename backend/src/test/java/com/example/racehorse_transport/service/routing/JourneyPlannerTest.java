package com.example.racehorse_transport.service.routing;

import com.example.racehorse_transport.service.routing.JourneyPlanner.Event;
import com.example.racehorse_transport.service.routing.JourneyPlanner.EventType;
import com.example.racehorse_transport.service.routing.JourneyPlanner.Journey;
import com.example.racehorse_transport.service.routing.JourneyPlanner.Leg;
import com.example.racehorse_transport.service.routing.JourneyPlanner.Rules;
import com.example.racehorse_transport.service.routing.JourneyPlanner.Stop;
import com.example.racehorse_transport.service.routing.JourneyPlanner.StopKind;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static com.example.racehorse_transport.service.routing.JourneyPlanner.EventType.*;
import static org.assertj.core.api.Assertions.assertThat;

// Lịch trình xe chở ngựa tính từ thời gian lái thật của từng chặng (không dùng vận tốc cố định).
// Quy định mặc định: lái liên tục tối đa 4 giờ rồi nghỉ 30 phút; tối đa 10 giờ lái mỗi ngày rồi nghỉ đêm 10 giờ;
// cửa khẩu mở 07:30–16:30, làm thủ tục 3 giờ. Mọi giờ là giờ Việt Nam; số kỳ vọng tính tay.
class JourneyPlannerTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Rules RULES = new Rules(4, 30, 10, 10, 3);
    private static final int OPEN = 7 * 60 + 30;
    private static final int CLOSE = 16 * 60 + 30;

    private static Instant at(String local) {
        return LocalDateTime.parse(local).atZone(VN).toInstant();
    }

    private static long hours(double h) {
        return Math.round(h * 3600);
    }

    private static Stop pickup() { return new Stop("Kho TP.HCM", StopKind.PICKUP, null, null); }
    private static Stop dropoff() { return new Stop("Hà Nội", StopKind.DROPOFF, null, null); }
    private static Stop gate() { return new Stop("Cửa khẩu Mộc Bài", StopKind.GATE, OPEN, CLOSE); }

    private static List<EventType> types(Journey journey) {
        return journey.events().stream().map(Event::type).toList();
    }

    @Test
    void shortTripIsOneDriveWithoutRest() {
        Journey journey = JourneyPlanner.plan(at("2026-11-02T05:00"), List.of(pickup(), dropoff()),
                List.of(new Leg(180, hours(3))), RULES, VN);

        assertThat(types(journey)).containsExactly(DRIVE);
        assertThat(journey.arrivalAt()).isEqualTo(at("2026-11-02T08:00"));
        assertThat(journey.borderArrivalAt()).isNull();
        assertThat(journey.crewDays()).isEqualTo(1);
    }

    @Test
    void restsHalfAnHourAfterEveryFourHoursOfDriving() {
        Journey journey = JourneyPlanner.plan(at("2026-11-02T05:00"), List.of(pickup(), dropoff()),
                List.of(new Leg(540, hours(9))), RULES, VN);

        assertThat(types(journey)).containsExactly(DRIVE, ROADSIDE_REST, DRIVE, ROADSIDE_REST, DRIVE);
        Event firstRest = journey.events().get(1);
        assertThat(firstRest.startAt()).isEqualTo(at("2026-11-02T09:00"));
        assertThat(firstRest.endAt()).isEqualTo(at("2026-11-02T09:30"));
        assertThat(firstRest.place()).isNull();
        assertThat(firstRest.kmIntoLeg()).isEqualTo(240.0); // 4/9 của 540 km
        assertThat(journey.arrivalAt()).isEqualTo(at("2026-11-02T15:00"));
        assertThat(journey.crewDays()).isEqualTo(1);
    }

    @Test
    void stopsForTheNightAfterTenHoursOfDriving() {
        Journey journey = JourneyPlanner.plan(at("2026-11-02T05:00"), List.of(pickup(), dropoff()),
                List.of(new Leg(900, hours(15))), RULES, VN);

        // 4 giờ, nghỉ, 4 giờ, nghỉ, 2 giờ (đủ 10 giờ) → nghỉ đêm 16:00–02:00 → 4 giờ, nghỉ, 1 giờ
        assertThat(types(journey)).containsExactly(
                DRIVE, ROADSIDE_REST, DRIVE, ROADSIDE_REST, DRIVE, OVERNIGHT, DRIVE, ROADSIDE_REST, DRIVE);
        Event night = journey.events().get(5);
        assertThat(night.startAt()).isEqualTo(at("2026-11-02T16:00"));
        assertThat(night.endAt()).isEqualTo(at("2026-11-03T02:00"));
        assertThat(night.kmIntoLeg()).isEqualTo(600.0); // 10/15 của 900 km
        assertThat(journey.arrivalAt()).isEqualTo(at("2026-11-03T07:30"));
        assertThat(journey.crewDays()).isEqualTo(2);
    }

    @Test
    void waitsForTheGateToOpenThenClearsCustoms() {
        Journey journey = JourneyPlanner.plan(at("2026-11-02T04:00"), List.of(pickup(), gate(), dropoff()),
                List.of(new Leg(70, hours(2)), new Leg(165, hours(3))), RULES, VN);

        assertThat(types(journey)).containsExactly(DRIVE, BORDER_WAIT, BORDER_PROCESSING, DRIVE);
        assertThat(journey.borderArrivalAt()).isEqualTo(at("2026-11-02T06:00"));
        assertThat(journey.events().get(1).endAt()).isEqualTo(at("2026-11-02T07:30"));
        assertThat(journey.events().get(2).place()).isEqualTo("Cửa khẩu Mộc Bài");
        assertThat(journey.events().get(2).endAt()).isEqualTo(at("2026-11-02T10:30"));
        assertThat(journey.arrivalAt()).isEqualTo(at("2026-11-02T13:30"));
        assertThat(journey.crewDays()).isEqualTo(1);
    }

    @Test
    void reachingTheGateAfterClosingWaitsUntilNextMorningAndCountsAsTheNightRest() {
        Journey journey = JourneyPlanner.plan(at("2026-11-02T12:00"), List.of(pickup(), gate(), dropoff()),
                List.of(new Leg(300, hours(5)), new Leg(120, hours(2))), RULES, VN);

        assertThat(types(journey)).containsExactly(
                DRIVE, ROADSIDE_REST, DRIVE, BORDER_WAIT, BORDER_PROCESSING, DRIVE);
        assertThat(journey.borderArrivalAt()).isEqualTo(at("2026-11-02T17:30"));
        assertThat(journey.events().get(3).endAt()).isEqualTo(at("2026-11-03T07:30"));
        assertThat(journey.arrivalAt()).isEqualTo(at("2026-11-03T12:30"));
        assertThat(journey.crewDays()).isEqualTo(2);
    }

    @Test
    void arrivingExactlyAtClosingTimeWaitsForTheNextDay() {
        Journey journey = JourneyPlanner.plan(at("2026-11-02T14:30"), List.of(pickup(), gate(), dropoff()),
                List.of(new Leg(120, hours(2)), new Leg(60, hours(1))), RULES, VN);

        assertThat(journey.borderArrivalAt()).isEqualTo(at("2026-11-02T16:30"));
        assertThat(journey.events().get(1).type()).isEqualTo(BORDER_WAIT);
        assertThat(journey.events().get(1).endAt()).isEqualTo(at("2026-11-03T07:30"));
    }

    @Test
    void arrivingExactlyAtOpeningTimeDoesNotWait() {
        Journey journey = JourneyPlanner.plan(at("2026-11-02T05:30"), List.of(pickup(), gate(), dropoff()),
                List.of(new Leg(120, hours(2)), new Leg(60, hours(1))), RULES, VN);

        assertThat(types(journey)).containsExactly(DRIVE, BORDER_PROCESSING, DRIVE);
        assertThat(journey.events().get(1).startAt()).isEqualTo(at("2026-11-02T07:30"));
    }

    @Test
    void gateWithoutOpeningHoursNeverMakesTheTruckWait() {
        Stop gateWithoutHours = new Stop("Cửa khẩu chưa khai báo giờ", StopKind.GATE, null, null);
        Journey journey = JourneyPlanner.plan(at("2026-11-02T20:00"), List.of(pickup(), gateWithoutHours, dropoff()),
                List.of(new Leg(60, hours(1)), new Leg(60, hours(1))), RULES, VN);

        assertThat(types(journey)).containsExactly(DRIVE, BORDER_PROCESSING, DRIVE);
        assertThat(journey.arrivalAt()).isEqualTo(at("2026-11-03T01:00"));
    }

    @Test
    void coordinatorChosenStationIsTheRestStop() {
        Stop station = new Stop("Trạm nghỉ Trảng Bàng", StopKind.VIA, null, null);
        Journey journey = JourneyPlanner.plan(at("2026-11-02T05:00"), List.of(pickup(), station, dropoff()),
                List.of(new Leg(180, hours(3)), new Leg(180, hours(3))), RULES, VN);

        // Nghỉ ở trạm đã chọn sau 3 giờ, nên chặng sau không phải dừng dọc đường
        assertThat(types(journey)).containsExactly(DRIVE, REST, DRIVE);
        assertThat(journey.events().get(1).place()).isEqualTo("Trạm nghỉ Trảng Bàng");
        assertThat(journey.arrivalAt()).isEqualTo(at("2026-11-02T11:30"));
    }

    @Test
    void zeroLengthLegAddsNoDriving() {
        Stop station = new Stop("Điểm đón thứ hai cùng chỗ", StopKind.VIA, null, null);
        Journey journey = JourneyPlanner.plan(at("2026-11-02T05:00"), List.of(pickup(), station, dropoff()),
                List.of(new Leg(0, 0), new Leg(60, hours(1))), RULES, VN);

        assertThat(types(journey)).containsExactly(REST, DRIVE);
        assertThat(journey.arrivalAt()).isEqualTo(at("2026-11-02T06:30"));
    }
}
