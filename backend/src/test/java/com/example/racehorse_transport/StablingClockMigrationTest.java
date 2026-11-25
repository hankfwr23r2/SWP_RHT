package com.example.racehorse_transport;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@PostgresIntegrationTest
class StablingClockMigrationTest {
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void migrationProvidesForeignKeysAndOneChargePerTripAndBooking() {
        assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_name = 'horse_stay'
                """, String.class)).contains("ready_at", "earliest_redelivery_at", "collection_reported_at",
                "missed_departure_id", "failed_delivery_leg_id");
        assertThat(jdbc.queryForObject("""
                SELECT indexdef FROM pg_indexes
                WHERE schemaname = current_schema() AND indexname = 'ux_cost_item_redelivery_trip_booking'
                """, String.class)).contains("UNIQUE", "vehicle_trip_id", "booking_id", "REDELIVERY");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM flyway_schema_history
                WHERE version IN ('202611282100', '202611282101') AND success
                """, Long.class)).isEqualTo(2L);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM system_config
                WHERE config_key IN ('STABLING_REDELIVERY_OFFER_HOURS', 'STABLING_COLLECTION_ADVANCE_HOURS',
                    'STABLING_COLLECTION_GRACE_HOURS')
                """, Long.class)).isEqualTo(3L);
    }
}
