package com.example.racehorse_transport.service.routing;

import com.example.racehorse_transport.service.routing.JourneyPlanner.Event;
import com.example.racehorse_transport.service.routing.JourneyPlanner.EventType;
import com.example.racehorse_transport.service.routing.JourneyPlanner.Journey;
import com.example.racehorse_transport.service.routing.JourneyPlanner.Leg;
import com.example.racehorse_transport.service.routing.JourneyPlanner.Rules;
import com.example.racehorse_transport.service.routing.JourneyPlanner.Station;
import com.example.racehorse_transport.service.routing.JourneyPlanner.Stop;
import com.example.racehorse_transport.service.routing.JourneyPlanner.StopKind;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static com.example.racehorse_transport.service.routing.JourneyPlanner.EventType.*;
import static org.assertj.core.api.Assertions.assertThat;

// Xe nghỉ ở trạm nghỉ thật nằm trên đường thay vì dừng dọc đường: trong khoảng từ 2 giờ tới 4 giờ lái liên tục,
// chọn trạm xa nhất; tới giới hạn giờ lái trong ngày thì nghỉ đêm ở trạm. Không có trạm phù hợp thì nghỉ dọc đường như cũ.
class JourneyPlannerStationTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Rules RULES = new Rules(4, 30, 10, 10, 3);

    private static Instant at(String local) {
        return LocalDateTime.parse(local).atZone(VN).toInstant();
    }

    private static long hours(double h) {
        return Math.round(h * 3600);
    }

    private static Station station(String name, double atHour, double kmPerHour) {
        return new Station(name, hours(atHour), atHour * kmPerHour);
    }

    private static final Stop PICKUP = new Stop("Kho TP.HCM", StopKind.PICKUP, null, null);
    private static final Stop DROPOFF = new Stop("Hà Nội", StopKind.DROPOFF, null, null);

    private static List<EventType> types(Journey journey) {
        return journey.events().stream().map(Event::type).toList();
    }

    @Test
    void restsAtTheFarthestStationBetweenTwoAndFourHours() {
        Journey journey = JourneyPlanner.plan(at("2026-11-02T05:00"), List.of(PICKUP, DROPOFF),
                List.of(new Leg(540, hours(9))),
                List.of(List.of(station("Trạm A", 2.5, 60), station("Trạm B", 3.5, 60))), RULES, VN);

        // Lái 3,5 giờ tới trạm B (xa nhất trong khoảng 2–4 giờ), nghỉ 30 phút; sau đó không còn trạm → nghỉ dọc đường ở mốc 4 giờ
        assertThat(types(journey)).containsExactly(DRIVE, REST, DRIVE, ROADSIDE_REST, DRIVE);
        Event rest = journey.events().get(1);
        assertThat(rest.place()).isEqualTo("Trạm B");
        assertThat(rest.startAt()).isEqualTo(at("2026-11-02T08:30"));
        assertThat(rest.endAt()).isEqualTo(at("2026-11-02T09:00"));
        assertThat(journey.events().get(3).startAt()).isEqualTo(at("2026-11-02T13:00"));
        assertThat(journey.arrivalAt()).isEqualTo(at("2026-11-02T15:00"));
    }

    @Test
    void stationBeforeTwoHoursIsIgnored() {
        Journey journey = JourneyPlanner.plan(at("2026-11-02T05:00"), List.of(PICKUP, DROPOFF),
                List.of(new Leg(540, hours(9))),
                List.of(List.of(station("Trạm quá sớm", 1.5, 60))), RULES, VN);

        assertThat(types(journey)).containsExactly(DRIVE, ROADSIDE_REST, DRIVE, ROADSIDE_REST, DRIVE);
        assertThat(journey.events().get(1).place()).isNull();
        assertThat(journey.events().get(1).startAt()).isEqualTo(at("2026-11-02T09:00"));
    }

    @Test
    void stationExactlyAtFourHoursIsUsed() {
        Journey journey = JourneyPlanner.plan(at("2026-11-02T05:00"), List.of(PICKUP, DROPOFF),
                List.of(new Leg(540, hours(9))),
                List.of(List.of(station("Trạm đúng 4 giờ", 4, 60))), RULES, VN);

        assertThat(types(journey).subList(0, 2)).containsExactly(DRIVE, REST);
        assertThat(journey.events().get(1).place()).isEqualTo("Trạm đúng 4 giờ");
        assertThat(journey.events().get(1).startAt()).isEqualTo(at("2026-11-02T09:00"));
    }

    @Test
    void stopsForTheNightAtAStationWhenTheDailyLimitComesFirst() {
        Journey journey = JourneyPlanner.plan(at("2026-11-02T05:00"), List.of(PICKUP, DROPOFF),
                List.of(new Leg(900, hours(15))),
                List.of(List.of(station("S1", 3.5, 60), station("S2", 7, 60), station("S3", 9.5, 60))), RULES, VN);

        // S1 lúc 08:30 (nghỉ), S2 lúc 12:30 (nghỉ), còn 3 giờ lái trong ngày → nghỉ đêm ở S3 lúc 15:30–01:30,
        // sau đó không còn trạm → nghỉ dọc đường lúc 05:30, tới nơi 07:30 ngày 03/11
        assertThat(types(journey)).containsExactly(
                DRIVE, REST, DRIVE, REST, DRIVE, OVERNIGHT, DRIVE, ROADSIDE_REST, DRIVE);
        assertThat(journey.events().get(1).place()).isEqualTo("S1");
        assertThat(journey.events().get(3).place()).isEqualTo("S2");
        Event night = journey.events().get(5);
        assertThat(night.place()).isEqualTo("S3");
        assertThat(night.startAt()).isEqualTo(at("2026-11-02T15:30"));
        assertThat(night.endAt()).isEqualTo(at("2026-11-03T01:30"));
        assertThat(journey.events().get(7).startAt()).isEqualTo(at("2026-11-03T05:30"));
        assertThat(journey.arrivalAt()).isEqualTo(at("2026-11-03T07:30"));
        assertThat(journey.crewDays()).isEqualTo(2);
    }

    @Test
    void stationsBelongToTheirOwnLeg() {
        Stop via = new Stop("Trạm điều phối chọn", StopKind.VIA, null, null);
        Journey journey = JourneyPlanner.plan(at("2026-11-02T05:00"), List.of(PICKUP, via, DROPOFF),
                List.of(new Leg(180, hours(3)), new Leg(360, hours(6))),
                List.of(List.of(station("Trạm trên chặng 1", 2.5, 60)), List.of(station("Trạm trên chặng 2", 3, 60))),
                RULES, VN);

        // Chặng 1 chỉ 3 giờ, không cần nghỉ giữa chặng; nghỉ ở trạm điều phối chọn; chặng 2 nghỉ ở trạm sau 3 giờ
        assertThat(types(journey)).containsExactly(DRIVE, REST, DRIVE, REST, DRIVE);
        assertThat(journey.events().get(1).place()).isEqualTo("Trạm điều phối chọn");
        assertThat(journey.events().get(3).place()).isEqualTo("Trạm trên chặng 2");
        assertThat(journey.events().get(3).startAt()).isEqualTo(at("2026-11-02T11:30"));
        assertThat(journey.arrivalAt()).isEqualTo(at("2026-11-02T15:00"));
    }
}
