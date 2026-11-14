package com.example.racehorse_transport;

import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import static org.assertj.core.api.Assertions.*;

class DatabaseConsolidationMigrationTest {
    @Test
    void preservesOtpAuditAndIncidentReferencesAndCanReplayBeforeCutover() throws Exception {
        try (var postgres = new PostgreSQLContainer("postgres:16-alpine")) {
            postgres.start();
            try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                legacySchema(connection);
                execute(connection, """
                    INSERT INTO users VALUES (1,'legacy-admin');
                    INSERT INTO booking VALUES (17);
                    INSERT INTO log(logid,note) VALUES (7,'existing event');
                    INSERT INTO incident VALUES (1,7);
                    INSERT INTO email_verification_otps VALUES (1,' User@Example.com ','hash-verify',now()+interval '10 minutes',now(),3);
                    INSERT INTO password_reset_otps VALUES (1,'user@example.com','hash-reset',now()+interval '8 minutes',now(),2);
                    INSERT INTO location_change_log VALUES (1,99,'locationName','old','new','removed-user',now());
                    INSERT INTO document_requirement_change_log VALUES (1,88,'created',null,'raw|snapshot','legacy-admin',now());
                    INSERT INTO system_log(system_logid,action_type,target_table,target_recordid,new_data,created_at,action_by_userid)
                        VALUES (1,'CREATE_BOOKING','BOOKING',17,'created',now(),1),
                               (2,'VEHICLE_RETURNED_HQ','BOOKING',17,'vehicle home',now(),1),
                               (3,'UPDATE_ACCOUNT','USER',1,'keep admin',now(),1);
                    SELECT setval(pg_get_serial_sequence('system_log','system_logid'),3);
                    """);
                migrate(connection, "001_unify_otp_and_audit.sql");
                migrate(connection, "001_unify_otp_and_audit.sql");
                assertThat(number(connection, "SELECT count(*) FROM email_otp")).isEqualTo(2);
                assertThat(number(connection, "SELECT attempts FROM email_otp WHERE purpose='VERIFY_EMAIL'")).isEqualTo(3);
                assertThat(number(connection, "SELECT count(*) FROM email_otp WHERE otp_hash IN ('hash-verify','hash-reset')")).isEqualTo(2);
                assertThat(number(connection, "SELECT count(*) FROM system_log WHERE source_id IS NOT NULL")).isEqualTo(2);
                assertThat(number(connection, "SELECT count(*) FROM system_log WHERE target_table IN ('BOOKING','INCIDENT')")).isZero();
                assertThat(number(connection, "SELECT count(*) FROM log WHERE source_system_log_id IS NOT NULL")).isEqualTo(2);
                assertThat(number(connection, "SELECT count(*) FROM log WHERE target_table='VEHICLE' AND bookingid IS NULL AND target_record_id=17")).isEqualTo(1);
                assertThat(number(connection, "SELECT logid FROM incident WHERE incidentid=1")).isEqualTo(7);
                execute(connection,"INSERT INTO log(note) VALUES ('new event')");
                assertThat(number(connection, "SELECT logid FROM log WHERE note='new event'")).isGreaterThan(7);
                migrate(connection, "002_remove_legacy_tables.sql");
                assertThat(number(connection,"SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('email_verification_otps','password_reset_otps','location_change_log','document_requirement_change_log')")).isZero();
            }
        }
    }

    @Test
    void refusesCaseFoldedOtpCollisionWithoutPartiallyChangingSchema() throws Exception {
        try (var postgres = new PostgreSQLContainer("postgres:16-alpine")) {
            postgres.start();
            try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                legacySchema(connection);
                execute(connection,"INSERT INTO email_verification_otps VALUES (1,'User@example.com','a',now(),now(),0),(2,'user@example.com','b',now(),now(),0)");
                assertThatThrownBy(() -> migrate(connection,"001_unify_otp_and_audit.sql"))
                        .isInstanceOf(SQLException.class).hasMessageContaining("Duplicate normalized OTP email");
                execute(connection,"ROLLBACK");
                assertThat(number(connection,"SELECT count(*) FROM email_verification_otps")).isEqualTo(2);
                assertThat(number(connection,"SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='email_otp'")).isZero();
            }
        }
    }

    private void legacySchema(Connection connection) throws SQLException {
        execute(connection,"""
            CREATE TABLE users(userid integer PRIMARY KEY,username varchar(255));
            CREATE TABLE booking(bookingid integer PRIMARY KEY);
            CREATE TABLE log(logid integer PRIMARY KEY,bookingid integer REFERENCES booking,staffid integer,
                log_type varchar(100),note text,status varchar(50),created_at timestamptz);
            CREATE TABLE incident(incidentid integer PRIMARY KEY,logid integer REFERENCES log);
            CREATE TABLE email_verification_otps(id bigint PRIMARY KEY,email varchar(255),otp_hash varchar(100),
                expires_at timestamptz,last_sent_at timestamptz,attempts integer);
            CREATE TABLE password_reset_otps(LIKE email_verification_otps INCLUDING ALL);
            CREATE TABLE location_change_log(id bigint PRIMARY KEY,locationid integer,field_name varchar(50),old_value text,new_value text,changed_by text,changed_at timestamptz);
            CREATE TABLE document_requirement_change_log(id bigint PRIMARY KEY,requirement_id integer,field_name varchar(50),old_value text,new_value text,changed_by text,changed_at timestamptz);
            CREATE TABLE system_log(system_logid integer GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                action_type varchar(100),target_table varchar(100),target_recordid integer,target_key varchar(255),
                actor_name varchar(255),actor_email varchar(255),actor_role varchar(50),target_label varchar(255),
                old_data text,new_data text,data_version integer,description text,created_at timestamptz,
                action_by_userid integer REFERENCES users);
            """);
    }

    private void migrate(Connection connection, String script) throws Exception {
        execute(connection, Files.readString(Path.of("scripts/migrations",script)));
    }
    private void execute(Connection connection,String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }
    private long number(Connection connection,String sql) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) { rows.next(); return rows.getLong(1); }
    }
}
