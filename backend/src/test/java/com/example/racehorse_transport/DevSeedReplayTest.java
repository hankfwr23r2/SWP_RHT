package com.example.racehorse_transport;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
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

    @Test
    void seedCanRunAgainAfterALinehaulTripUsedTheRoute() throws Exception {
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
}
