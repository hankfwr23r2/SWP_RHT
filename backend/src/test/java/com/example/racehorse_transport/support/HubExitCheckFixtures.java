package com.example.racehorse_transport.support;

import com.example.racehorse_transport.entity.enums.HealthCheckType;
import org.springframework.jdbc.core.JdbcTemplate;

public final class HubExitCheckFixtures {
    private HubExitCheckFixtures() {}

    public static void boardingDone(JdbcTemplate jdbc, long tripId, long recorderId, HealthCheckType type) {
        var horses = jdbc.query("""
                SELECT l.booking_horse_id, s.id, t.coordinator_id
                FROM horse_leg l JOIN vehicle_trip t ON t.id = l.vehicle_trip_id
                JOIN trip_stop p ON p.id = l.board_trip_stop_id
                JOIN horse_stay s ON s.booking_horse_id = l.booking_horse_id AND s.location_id = p.location_id
                WHERE t.id = ? AND l.status = 'PLANNED' AND s.actual_start_at IS NOT NULL AND s.actual_end_at IS NULL
                """, (row, index) -> new Stay(row.getLong(1), row.getLong(2), row.getLong(3)), tripId);
        if (horses.isEmpty()) throw new AssertionError("Cần lưu trú tại điểm lên xe để dựng việc khám.");
        for (Stay stay : horses) {
            recordDone(jdbc, stay.horseId(), stay.id(), tripId, stay.coordinatorId(), recorderId, type);
        }
    }

    public static void collectionDone(JdbcTemplate jdbc, long stayId, long coordinatorId) {
        long horseId = jdbc.queryForObject("SELECT booking_horse_id FROM horse_stay WHERE id = ?", Long.class, stayId);
        recordDone(jdbc, horseId, stayId, null, coordinatorId, coordinatorId, HealthCheckType.BEFORE_DELIVERY);
    }

    private static void recordDone(JdbcTemplate jdbc, long horseId, long stayId, Long tripId, long coordinatorId,
                                   long recorderId, HealthCheckType type) {
        long log = jdbc.queryForObject("""
                INSERT INTO horse_health_log (booking_horse_id, recorded_by, recorded_at, horse_stay_id, health_status)
                VALUES (?, ?, now(), ?, 'NORMAL') RETURNING id
                """, Long.class, horseId, recorderId, stayId);
        jdbc.update("""
                INSERT INTO health_check_task (booking_horse_id, vehicle_trip_id, horse_stay_id, assigned_to, assigned_by,
                    assigned_at, check_type, due_at, status, done_at, health_log_id)
                VALUES (?, ?, ?, ?, ?, now(), ?, now(), 'DONE', now(), ?)
                """, horseId, tripId, tripId == null ? stayId : null, recorderId, coordinatorId, type.name(), log);
    }

    private record Stay(long horseId, long id, long coordinatorId) {}
}
