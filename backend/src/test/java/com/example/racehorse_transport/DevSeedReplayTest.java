package com.example.racehorse_transport;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.dto.hub.HubOverviewResponseDto.HubFleetRequirementResponseDto;
import com.example.racehorse_transport.service.HubFleetRequirementService;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

// Flyway chạy lại dữ liệu mẫu mỗi khi file đổi. Máy đã có lượt xe trục vẫn phải khởi động được.
@PostgresIntegrationTest
@Transactional
class DevSeedReplayTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private HubFleetRequirementService fleetRequirements;

    @Test
    void seedCanRunAgainAfterALinehaulTripUsedTheRoute() throws Exception {
        jdbc.update("""
                INSERT INTO departure_schedule_day (departure_schedule_id, day_of_week)
                SELECT s.id, 7 FROM departure_schedule s JOIN route r ON r.id = s.route_id
                WHERE r.code = 'HCM-DN' AND s.status = 'ACTIVE'
                """);
        Timestamp at = Timestamp.valueOf("2099-01-01 05:00:00");
        Long trip = jdbc.queryForObject("""
                INSERT INTO vehicle_trip (code, trip_type, departure_id, vehicle_id, start_location_id, end_location_id, planned_start_at, planned_end_at, status)
                SELECT 'VT-SEED-REPLAY', 'LINEHAUL', d.id, (SELECT min(id) FROM vehicle), r.origin_location_id, r.destination_location_id, ?, ?, 'PLANNED'
                FROM departure d JOIN route r ON r.id = d.route_id WHERE r.code = 'HCM-DN' ORDER BY d.id LIMIT 1 RETURNING id
                """, Long.class, at, Timestamp.valueOf("2099-01-03 05:00:00"));
        jdbc.update("""
                INSERT INTO trip_stop (vehicle_trip_id, sequence_number, location_id, stop_type, route_id, route_stop_sequence_number, planned_arrival_at, planned_departure_at)
                SELECT ?, rs.sequence_number, rs.location_id, rs.stop_type, rs.route_id, rs.sequence_number, ?, ?
                FROM route_stop rs JOIN route r ON r.id = rs.route_id WHERE r.code = 'HCM-DN'
                """, trip, at, at);

        jdbc.execute(new ClassPathResource("db/seed-dev/R__dev_seed_base.sql").getContentAsString(StandardCharsets.UTF_8));

        jdbc.execute(new ClassPathResource("db/seed-dev/R__dev_seed_bookings.sql").getContentAsString(StandardCharsets.UTF_8));

        assertThat(jdbc.queryForList("""
                SELECT sd.day_of_week FROM departure_schedule_day sd
                JOIN departure_schedule s ON s.id = sd.departure_schedule_id
                JOIN route r ON r.id = s.route_id WHERE r.code = 'HCM-DN' AND s.status = 'ACTIVE'
                """, Integer.class)).containsExactly(6);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM vehicle_trip vt JOIN departure d ON d.id = vt.departure_id WHERE vt.id = ?
                """, Integer.class, trip)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM trip_stop WHERE vehicle_trip_id = ?", Integer.class, trip)).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM route_stop rs JOIN route r ON r.id = rs.route_id WHERE r.code = 'HCM-DN'", Integer.class)).isEqualTo(8);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM booking b
                JOIN departure d ON d.id = b.departure_id
                JOIN route r ON r.id = d.route_id
                JOIN location destination ON destination.id = r.destination_location_id
                JOIN booking_address a ON a.booking_id = b.id AND a.address_type = 'DELIVERY'
                JOIN hub_service_area area ON area.province_code = a.province_code AND area.hub_location_id = destination.id
                WHERE b.code = 'DEV-EXPORT-001' AND r.code = 'HCM-PNH' AND destination.code = 'HUB-PNH'
                  AND b.requested_pickup_date <= d.departure_date - 1
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void replayedRoutesAndTimetableMatchTheRequiredFleetForAllEightHubs() throws Exception {
        jdbc.update("UPDATE route SET fleet_outbound = NOT fleet_outbound WHERE code IN ('HCM-NT', 'NT-HCM')");
        jdbc.execute(new ClassPathResource("db/seed-dev/R__dev_seed_base.sql").getContentAsString(StandardCharsets.UTF_8));
        assertThat(jdbc.queryForList("SELECT code FROM route WHERE fleet_outbound", String.class))
                .containsExactlyInAnyOrder("HCM-NT", "NT-DN", "HCM-DN", "DN-VINH", "VINH-HN",
                        "DN-HN", "HCM-HN", "HCM-PNH", "DN-SVK", "VINH-VTE");

        Map<String, List<Long>> expected = Map.of(
                "HUB-HCM", List.of(5L, 1L, 3L, 1L, 1L, 11L, 11L),
                "HUB-NT", List.of(1L, 0L, 1L, 1L, 1L, 4L, 4L),
                "HUB-DN", List.of(3L, 1L, 2L, 1L, 1L, 8L, 8L),
                "HUB-VINH", List.of(2L, 1L, 1L, 1L, 1L, 5L, 5L),
                "HUB-HN", List.of(0L, 0L, 2L, 1L, 1L, 4L, 4L),
                "HUB-PNH", List.of(0L, 0L, 1L, 1L, 1L, 3L, 3L),
                "HUB-VTE", List.of(0L, 0L, 1L, 1L, 1L, 3L, 3L),
                "HUB-SVK", List.of(0L, 0L, 1L, 1L, 1L, 3L, 3L));
        Map<String, Long> hubIds = new HashMap<>();
        jdbc.query("SELECT code, id FROM location WHERE location_type = 'HUB' AND status = 'ACTIVE'", row -> {
            if (expected.containsKey(row.getString("code"))) hubIds.put(row.getString("code"), row.getLong("id"));
        });
        assertThat(hubIds.keySet()).isEqualTo(expected.keySet());
        // 00:00 ngày 1/11 ở Việt Nam, ngày đầu lịch mẫu có hiệu lực.
        var requirements = fleetRequirements.requirements(hubIds.values(), Instant.parse("2026-10-31T17:00:00Z"), Map.of());
        expected.forEach((hub, counts) -> {
            List<HubFleetRequirementResponseDto> rows = requirements.get(hubIds.get(hub));
            var branch = rows.get(2).breakdown();
            assertThat(List.of(rows.get(0).required(), rows.get(1).required(), branch.sixSeat(), branch.twoSeat(), branch.spare(),
                    rows.get(3).required(), rows.get(4).required())).as(hub).isEqualTo(counts);
            assertThat(rows.get(2).required()).as(hub).isEqualTo(branch.sixSeat() + branch.twoSeat() + branch.spare());
        });
    }
}
