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

    @Test
    void addsUserAndStaffForeignKeysWithBackfillAndCanReplay() throws Exception {
        try (var postgres = new PostgreSQLContainer("postgres:16-alpine")) {
            postgres.start();
            try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                execute(connection, """
                    CREATE TABLE users(userid integer PRIMARY KEY,username varchar(255),email varchar(255));
                    CREATE TABLE staff(userid integer PRIMARY KEY REFERENCES users,role varchar(100));
                    CREATE TABLE email_otp(id bigint PRIMARY KEY,email varchar(255) NOT NULL,purpose varchar(30) NOT NULL);
                    CREATE TABLE document_requirement(id integer PRIMARY KEY,updated_by varchar(100));
                    CREATE TABLE system_config(config_key varchar(100) PRIMARY KEY,updated_by varchar(100));
                    INSERT INTO system_config VALUES ('DEPOSIT_RATE','admin01'),('INSURANCE_RATE',null),('MARGIN_RATE','customer');
                    INSERT INTO users VALUES (1,'admin01',' Admin@Example.com '),(2,'customer','cus@example.com');
                    INSERT INTO staff VALUES (1,'ADMIN');
                    INSERT INTO email_otp VALUES (1,'admin@example.com','VERIFY_EMAIL'),(2,'cus@example.com','RESET_PASSWORD'),
                        (3,'ghost@example.com','VERIFY_EMAIL');
                    INSERT INTO document_requirement VALUES (1,'admin01'),(2,'SYSTEM'),(3,'customer'),(4,null);
                    """);
                migrate(connection, "003_add_user_staff_fk.sql");
                migrate(connection, "003_add_user_staff_fk.sql");
                assertThat(number(connection, "SELECT userid FROM email_otp WHERE id=1")).isEqualTo(1);
                assertThat(number(connection, "SELECT userid FROM email_otp WHERE id=2")).isEqualTo(2);
                assertThat(number(connection, "SELECT count(*) FROM email_otp WHERE id=3")).isZero();
                assertThat(number(connection, "SELECT updated_by_staffid FROM document_requirement WHERE id=1")).isEqualTo(1);
                // 'SYSTEM' và khách hàng (không phải staff) không có staff tương ứng -> NULL
                assertThat(number(connection, "SELECT count(*) FROM document_requirement WHERE updated_by_staffid IS NULL")).isEqualTo(3);
                assertThat(number(connection, "SELECT count(*) FROM information_schema.columns WHERE table_name='document_requirement' AND column_name='updated_by'")).isZero();
                assertThat(number(connection, "SELECT count(*) FROM pg_constraint WHERE conname IN ('fk_email_otp_user','fk_document_requirement_updated_by_staff','fk_system_config_updated_by_staff')")).isEqualTo(3);
                assertThat(number(connection, "SELECT updated_by_staffid FROM system_config WHERE config_key='DEPOSIT_RATE'")).isEqualTo(1);
                assertThat(number(connection, "SELECT count(*) FROM system_config WHERE updated_by_staffid IS NULL")).isEqualTo(2);
                assertThat(number(connection, "SELECT count(*) FROM information_schema.columns WHERE table_name='system_config' AND column_name='updated_by'")).isZero();
                assertThatThrownBy(() -> execute(connection, "UPDATE document_requirement SET updated_by_staffid=2 WHERE id=4"))
                        .isInstanceOf(SQLException.class).hasMessageContaining("fk_document_requirement_updated_by_staff");
                execute(connection, "DELETE FROM users WHERE userid=2");
                assertThat(number(connection, "SELECT count(*) FROM email_otp WHERE id=2")).isZero();
            }
        }
    }

    @Test
    void mergesRegulationIntoDocumentRequirementAndCanReplay() throws Exception {
        try (var postgres = new PostgreSQLContainer("postgres:16-alpine")) {
            postgres.start();
            try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                execute(connection, """
                    CREATE TABLE users(userid integer PRIMARY KEY);
                    CREATE TABLE staff(userid integer PRIMARY KEY REFERENCES users);
                    CREATE TABLE location(locationid integer PRIMARY KEY,destination_country varchar(2));
                    CREATE TABLE document_requirement(id integer GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        document_group varchar(20) NOT NULL,document_type varchar(50) NOT NULL,document_name varchar(255) NOT NULL,
                        description varchar(1000),trip_type varchar(20) NOT NULL,country varchar(10) NOT NULL,
                        required boolean NOT NULL,status varchar(10) NOT NULL,updated_at timestamptz,
                        updated_by_staffid integer REFERENCES staff);
                    CREATE TABLE regulation(regulationid integer PRIMARY KEY,staffid integer REFERENCES staff,
                        locationid integer REFERENCES location,document_type varchar(50),country varchar(100),
                        regulation_name varchar(255),description text,status varchar(8));
                    CREATE TABLE trip_document(trip_documentid integer PRIMARY KEY,regulationid integer REFERENCES regulation);
                    INSERT INTO users VALUES (7); INSERT INTO staff VALUES (7);
                    INSERT INTO location VALUES (16,'KH'),(24,NULL);
                    INSERT INTO document_requirement(document_group,document_type,document_name,trip_type,country,required,status)
                        VALUES ('CLEARANCE','poa','Giấy ủy quyền','ALL','ALL',true,'ACTIVE');
                    INSERT INTO regulation VALUES
                        (1,7,NULL,'poa','Việt Nam','Ủy quyền cũ','x','ACTIVE'),
                        (2,7,NULL,'health_cert','Việt Nam','Kiểm dịch','y','ACTIVE'),
                        (3,7,16,'customs_declaration','Campuchia','Tờ khai Mộc Bài','z','ACTIVE'),
                        (4,7,24,'ata_carnet','Lào','ATA Lao Bảo','w','INACTIVE');
                    INSERT INTO trip_document VALUES (1,3);
                    """);
                migrate(connection, "004_merge_regulation_into_document_requirement.sql");
                migrate(connection, "004_merge_regulation_into_document_requirement.sql");
                assertThat(number(connection, "SELECT count(*) FROM information_schema.tables WHERE table_name='regulation'")).isZero();
                assertThat(number(connection, "SELECT count(*) FROM information_schema.columns WHERE table_name='trip_document' AND column_name='regulationid'")).isZero();
                // poa chung đã có -> không nhân bản; health_cert chung được tạo
                assertThat(number(connection, "SELECT count(*) FROM document_requirement WHERE document_type='poa'")).isEqualTo(1);
                assertThat(number(connection, "SELECT count(*) FROM document_requirement WHERE document_type='health_cert' AND locationid IS NULL AND country='ALL'")).isEqualTo(1);
                assertThat(number(connection, "SELECT count(*) FROM document_requirement WHERE locationid=16 AND country='KH' AND trip_type='INTERNATIONAL' AND document_name='Tờ khai Mộc Bài' AND updated_by_staffid=7")).isEqualTo(1);
                // Cửa khẩu chưa có destination_country -> lấy quốc gia của quy định cũ; giữ trạng thái
                assertThat(number(connection, "SELECT count(*) FROM document_requirement WHERE locationid=24 AND country='LA' AND status='INACTIVE'")).isEqualTo(1);
                assertThatThrownBy(() -> execute(connection, "UPDATE document_requirement SET locationid=999 WHERE locationid=16"))
                        .isInstanceOf(SQLException.class).hasMessageContaining("fk_document_requirement_location");
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
