package com.example.racehorse_transport;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.entity.enums.LocationType;
import com.example.racehorse_transport.entity.enums.RouteStopType;
import com.example.racehorse_transport.policy.RouteStopsPolicy;
import com.example.racehorse_transport.service.SystemConfigService;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

// Mọi tuyến mẫu phải đạt quy tắc #31 như khi quản trị viên lưu tuyến
@PostgresIntegrationTest
class DevSeedRoutesTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private SystemConfigService config;

    private record Ends(long originId, LocationType originType, long destinationId, LocationType destinationType) {
    }

    @ParameterizedTest
    @ValueSource(strings = {"HCM-PNH", "MOCBAI-HCM", "HCM-DN", "DN-HCM", "DN-LAOBAO", "LAOBAO-DN"})
    void seedRouteSatisfiesStopRules(String code) {
        Ends ends = jdbc.queryForObject("""
                SELECT o.id, o.location_type, d.id, d.location_type FROM route r
                JOIN location o ON o.id = r.origin_location_id JOIN location d ON d.id = r.destination_location_id
                WHERE r.code = ?
                """, (rs, i) -> new Ends(rs.getLong(1), LocationType.valueOf(rs.getString(2)),
                rs.getLong(3), LocationType.valueOf(rs.getString(4))), code);
        List<RouteStopsPolicy.Stop> stops = jdbc.query("""
                SELECT s.sequence_number, l.id, l.location_type, s.stop_type, s.arrival_offset_minutes, s.dwell_minutes
                FROM route_stop s JOIN route r ON r.id = s.route_id JOIN location l ON l.id = s.location_id WHERE r.code = ?
                """, (rs, i) -> new RouteStopsPolicy.Stop(rs.getInt(1), rs.getLong(2), LocationType.valueOf(rs.getString(3)),
                RouteStopType.valueOf(rs.getString(4)), rs.getInt(5), rs.getInt(6)), code);
        var limits = new RouteStopsPolicy.Limits(config.getInt("MAX_CONTINUOUS_HOURS"), config.getInt("MIN_REST_MINUTES"),
                config.getInt("MAX_DRIVE_HOURS_PER_DAY"), config.getInt("OVERNIGHT_REST_HOURS"));

        assertThat(RouteStopsPolicy.violations(ends.originId(), ends.originType(), ends.destinationId(),
                ends.destinationType(), stops, limits)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"HCM-PNH", "MOCBAI-HCM", "HCM-DN", "DN-HCM", "DN-LAOBAO", "LAOBAO-DN"})
    void seedRouteRunsOnceAWeek(String code) {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM departure_schedule_day dd JOIN departure_schedule s ON s.id = dd.departure_schedule_id
                JOIN route r ON r.id = s.route_id
                WHERE r.code = ? AND s.valid_from = DATE '2026-11-01' AND s.booking_cutoff_hours = 72 AND s.stall_capacity = 9
                """, Integer.class, code)).isEqualTo(1);
    }
}
