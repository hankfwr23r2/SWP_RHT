package com.example.racehorse_transport;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.entity.enums.LocationType;
import com.example.racehorse_transport.entity.enums.RouteStopType;
import com.example.racehorse_transport.policy.RouteStopsPolicy;
import com.example.racehorse_transport.service.SystemConfigService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

// Mọi tuyến mẫu phải đạt quy tắc #31 như khi quản trị viên lưu tuyến
@PostgresIntegrationTest
class DevSeedRoutesTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private SystemConfigService config;

    @Test
    void unservedProvincesHaveNoAssignedHub() {
        assertThat(jdbc.queryForList("""
                SELECT province_code FROM hub_service_area
                WHERE province_code IN ('KH-1', 'KH-16', 'LA-BK', 'LA-LM', 'LA-PH')
                """, String.class)).isEmpty();
    }

    @Test
    void reverseTripsLeaveEnoughRestAndLegacySchedulesAreEnded() {
        // HN-HCM là lượt về của vòng hai tuần; chuyến HCM-HN kế tiếp do xe luân phiên chạy.
        assertThat(jdbc.queryForList("""
                WITH timetable AS (
                    SELECT r.code, r.origin_location_id AS origin, r.destination_location_id AS destination,
                           o.country_code <> d.country_code AS international, last_stop.arrival_offset_minutes AS duration,
                           s.departure_time, (dd.day_of_week - 1) * 1440
                               + extract(epoch FROM s.departure_time)::int / 60 AS minute_of_week
                    FROM route r
                    JOIN location o ON o.id = r.origin_location_id
                    JOIN location d ON d.id = r.destination_location_id
                    JOIN departure_schedule s ON s.route_id = r.id AND s.status = 'ACTIVE'
                    JOIN departure_schedule_day dd ON dd.departure_schedule_id = s.id
                    LEFT JOIN LATERAL (
                        SELECT rs.arrival_offset_minutes FROM route_stop rs
                        WHERE rs.route_id = r.id ORDER BY rs.sequence_number DESC LIMIT 1
                    ) last_stop ON true
                    WHERE r.status = 'ACTIVE' AND s.valid_from = DATE '2026-11-01'
                )
                SELECT outbound.code FROM timetable outbound
                LEFT JOIN timetable inbound ON inbound.origin = outbound.destination AND inbound.destination = outbound.origin
                WHERE inbound.code IS NULL OR outbound.duration IS NULL OR outbound.departure_time <>
                    CASE WHEN outbound.international THEN TIME '06:00' ELSE TIME '05:00' END
                    OR (inbound.minute_of_week - outbound.minute_of_week + 10080) % 10080
                        + CASE WHEN outbound.code = 'HN-HCM' THEN 10080 ELSE 0 END < outbound.duration + 720
                """, String.class)).isEmpty();
        assertThat(jdbc.queryForList("""
                SELECT r.code FROM route r JOIN departure_schedule s ON s.route_id = r.id
                WHERE r.status = 'INACTIVE' AND s.status = 'ENDED'
                  AND r.code IN ('MOCBAI-HCM', 'DN-LAOBAO', 'LAOBAO-DN')
                """, String.class)).containsExactlyInAnyOrder("MOCBAI-HCM", "DN-LAOBAO", "LAOBAO-DN");
    }

    private record Ends(long originId, LocationType originType, long destinationId, LocationType destinationType, boolean international) {
    }

    @ParameterizedTest
    @ValueSource(strings = {"HCM-NT", "NT-HCM", "NT-DN", "DN-NT", "HCM-DN", "DN-HCM", "DN-VINH", "VINH-DN",
            "VINH-HN", "HN-VINH", "DN-HN", "HN-DN", "HCM-HN", "HN-HCM", "HCM-PNH", "PNH-HCM",
            "DN-SVK", "SVK-DN", "VINH-VTE", "VTE-VINH"})
    void seedRouteSatisfiesStopRules(String code) {
        Ends ends = jdbc.queryForObject("""
                SELECT o.id, o.location_type, d.id, d.location_type, o.country_code <> d.country_code FROM route r
                JOIN location o ON o.id = r.origin_location_id JOIN location d ON d.id = r.destination_location_id
                WHERE r.code = ? AND r.status = 'ACTIVE'
                """, (rs, i) -> new Ends(rs.getLong(1), LocationType.valueOf(rs.getString(2)),
                rs.getLong(3), LocationType.valueOf(rs.getString(4)), rs.getBoolean(5)), code);
        List<RouteStopsPolicy.Stop> stops = jdbc.query("""
                SELECT s.sequence_number, l.id, l.location_type, s.stop_type, s.arrival_offset_minutes, s.dwell_minutes
                FROM route_stop s JOIN route r ON r.id = s.route_id JOIN location l ON l.id = s.location_id WHERE r.code = ? ORDER BY s.sequence_number
                """, (rs, i) -> new RouteStopsPolicy.Stop(rs.getInt(1), rs.getLong(2), LocationType.valueOf(rs.getString(3)),
                RouteStopType.valueOf(rs.getString(4)), rs.getInt(5), rs.getInt(6)), code);
        assertThat(ends.originType()).isEqualTo(LocationType.HUB);
        assertThat(ends.destinationType()).isEqualTo(LocationType.HUB);
        if (ends.international()) {
            assertThat(stops.subList(1, stops.size() - 1)).anyMatch(stop -> stop.stopType() == RouteStopType.BORDER);
        }
        for (int i = 1; i < stops.size(); i++) {
            var previous = stops.get(i - 1);
            assertThat(stops.get(i).arrivalOffsetMinutes() - previous.arrivalOffsetMinutes() - previous.dwellMinutes())
                    .isBetween(0, 240);
        }
        var limits = new RouteStopsPolicy.Limits(config.getInt("MAX_CONTINUOUS_HOURS"), config.getInt("MIN_REST_MINUTES"),
                config.getInt("MAX_DRIVE_HOURS_PER_DAY"), config.getInt("OVERNIGHT_REST_HOURS"));

        assertThat(RouteStopsPolicy.violations(ends.originId(), ends.originType(), ends.destinationId(),
                ends.destinationType(), stops, limits)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "HCM-NT,6", "NT-HCM,1", "NT-DN,3", "DN-NT,4", "HCM-DN,6", "DN-HCM,1",
            "DN-VINH,6", "VINH-DN,1", "VINH-HN,3", "HN-VINH,4", "DN-HN,5", "HN-DN,1",
            "HCM-HN,3", "HN-HCM,1", "HCM-PNH,6", "PNH-HCM,1", "DN-SVK,5", "SVK-DN,1",
            "VINH-VTE,6", "VTE-VINH,1"
    })
    void seedRouteRunsOnceAWeekOnTheApprovedDay(String code, int day) {
        assertThat(jdbc.queryForList("""
                SELECT dd.day_of_week FROM departure_schedule_day dd
                JOIN departure_schedule s ON s.id = dd.departure_schedule_id
                JOIN route r ON r.id = s.route_id
                WHERE r.code = ? AND s.status = 'ACTIVE'
                  AND s.valid_from = DATE '2026-11-01' AND s.booking_cutoff_hours = 72 AND s.base_vehicle_count = 1 AND s.base_vehicle_class_id = (SELECT id FROM vehicle_class WHERE code = 'HEAVY')
                """, Integer.class, code)).containsExactly(day);
    }

    @Test
    void noSeedScheduleRunsOnSunday() {
        assertThat(jdbc.queryForList("""
                SELECT r.code FROM departure_schedule_day dd
                JOIN departure_schedule s ON s.id = dd.departure_schedule_id
                JOIN route r ON r.id = s.route_id
                WHERE s.status = 'ACTIVE' AND dd.day_of_week = 7
                """, String.class)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "HUB-HCM,6,4,1,11", "HUB-NT,1,2,1,4", "HUB-DN,4,3,1,8", "HUB-VINH,3,2,1,5",
            "HUB-HN,0,3,1,4", "HUB-PNH,0,2,1,3", "HUB-VTE,0,2,1,3", "HUB-SVK,0,2,1,3"
    })
    void fleetAndStaffMatchApprovedCounts(String hub, int heavy, int medium, int light, int perRole) {
        assertThat(jdbc.queryForMap("""
                SELECT count(*) FILTER (WHERE vc.code = 'HEAVY') AS heavy,
                       count(*) FILTER (WHERE vc.code = 'MEDIUM') AS medium,
                       count(*) FILTER (WHERE vc.code = 'LIGHT') AS light
                FROM vehicle v JOIN vehicle_class vc ON vc.id = v.vehicle_class_id
                JOIN location l ON l.id = v.home_hub_location_id WHERE l.code = ?
                """, hub)).containsEntry("heavy", (long) heavy)
                .containsEntry("medium", (long) medium).containsEntry("light", (long) light);
        assertThat(jdbc.queryForMap("""
                SELECT count(*) FILTER (WHERE u.role_code = 'DRIVER') AS drivers,
                       count(*) FILTER (WHERE u.role_code = 'ESCORT') AS escorts
                FROM staff s JOIN app_user u ON u.id = s.user_id
                JOIN location l ON l.id = s.home_hub_location_id WHERE l.code = ?
                """, hub)).containsEntry("drivers", (long) perRole).containsEntry("escorts", (long) perRole);
    }

    @Test
    void seedVehiclesProvideTheApprovedCapacity() {
        assertThat(jdbc.queryForList("""
                SELECT v.license_plate FROM vehicle v JOIN vehicle_class vc ON vc.id = v.vehicle_class_id
                WHERE v.standard_stall_count + 2 * v.wide_stall_count
                    <> CASE vc.code WHEN 'HEAVY' THEN 9 WHEN 'MEDIUM' THEN 6 WHEN 'LIGHT' THEN 2 END
                   OR v.standard_stall_count + 2 * v.wide_stall_count > vc.max_stall_count
                """, String.class)).isEmpty();
    }
}
