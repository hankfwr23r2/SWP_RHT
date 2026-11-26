package com.example.racehorse_transport;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@PostgresIntegrationTest
class PickupMissPricingMigrationTest {
    @Autowired private JdbcTemplate jdbc;

    @Test
    void validatesTheAddOnCompletionColumnAndQuotationFloorConstraint() {
        assertThat(jdbc.queryForObject("""
                select data_type from information_schema.columns
                where table_schema = current_schema() and table_name = 'booking_horse_service'
                  and column_name = 'completed_at'
                """, String.class)).isEqualTo("timestamp with time zone");
        assertThat(jdbc.queryForObject("""
                select convalidated from pg_constraint where conname = 'ck_quotation_line_pickup_deposit_floor'
                """, Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("""
                select convalidated from pg_constraint where conname = 'ck_horse_insurance_refunded_premium'
                """, Boolean.class)).isTrue();
    }
}
