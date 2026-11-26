package com.example.racehorse_transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.dto.health.HubStallChangeRequestDto;
import com.example.racehorse_transport.dto.health.HubStallCreateRequestDto;
import com.example.racehorse_transport.dto.health.HubStallResponseDto;
import com.example.racehorse_transport.entity.enums.HubStallKind;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.service.HubStallService;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@PostgresIntegrationTest
class HubStallIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private HubStallService stalls;

    private long hub;
    private long line;

    @BeforeEach
    void locateSeedData() {
        hub = jdbc.queryForObject("SELECT id FROM location WHERE code = 'HUB-HCM'", Long.class);
        line = jdbc.queryForObject("SELECT min(id) FROM booking_horse", Long.class);
    }

    @Test
    void databaseRejectsTwoConcurrentOpenStaysInTheSameStall() throws Exception {
        var stall = stalls.create(hub, new HubStallCreateRequestDto("DB-" + System.nanoTime(), HubStallKind.STANDARD));
        long secondLine = jdbc.queryForObject("SELECT min(id) FROM booking_horse WHERE id <> ?", Long.class, line);
        var tx = new TransactionTemplate(transactions);
        var firstInserted = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);

        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> tx.executeWithoutResult(status -> {
                insertStay(line, stall.id());
                firstInserted.countDown();
                await(secondStarted);
            }));
            var second = workers.submit(() -> tx.executeWithoutResult(status -> {
                await(firstInserted);
                secondStarted.countDown();
                insertStay(secondLine, stall.id());
            }));

            first.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> second.get(10, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                    .rootCause().isInstanceOfSatisfying(SQLException.class, error -> {
                        assertThat(error.getSQLState()).isEqualTo("23505");
                        assertThat(error.getMessage()).contains("uk_horse_stay_open_hub_stall");
                    });
            assertThat(jdbc.queryForObject("SELECT count(*) FROM horse_stay WHERE hub_stall_id = ?", Long.class, stall.id()))
                    .isEqualTo(1L);
        } finally {
            jdbc.update("DELETE FROM horse_stay WHERE hub_stall_id = ?", stall.id());
            jdbc.update("DELETE FROM hub_stall WHERE id = ?", stall.id());
        }
    }

    @Test
    void movesToIsolationAndReleasesSpaceWhenStayCloses() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var standard = stalls.create(hub, new HubStallCreateRequestDto("TEST-S", HubStallKind.STANDARD));
            var isolation = stalls.create(hub, new HubStallCreateRequestDto("TEST-I", HubStallKind.ISOLATION));
            assertThatThrownBy(() -> stalls.create(hub, new HubStallCreateRequestDto("TEST-S", HubStallKind.STANDARD)))
                    .isInstanceOf(DispatchRuleException.class);
            long stay = insertStay(line, standard.id());
            assertThat(row(standard.id()).occupied()).isTrue();
            assertThatThrownBy(() -> stalls.deactivate(hub, standard.id())).isInstanceOf(DispatchRuleException.class);
            assertThatThrownBy(() -> stalls.move(stay, new HubStallChangeRequestDto(standard.id())))
                    .isInstanceOf(DispatchRuleException.class);

            assertThat(stalls.move(stay, new HubStallChangeRequestDto(isolation.id())).occupied()).isTrue();
            assertThat(row(standard.id()).occupied()).isFalse();

            jdbc.update("UPDATE horse_stay SET actual_end_at = now() WHERE id = ?", stay);
            assertThat(row(isolation.id()).occupied()).isFalse();
            assertThat(stalls.deactivate(hub, isolation.id()).active()).isFalse();
            status.setRollbackOnly();
        });
    }

    private HubStallResponseDto row(long id) {
        return stalls.inventory(hub).stalls().stream().filter(stall -> stall.id() == id).findFirst().orElseThrow();
    }

    private long insertStay(long horse, long stall) {
        return jdbc.queryForObject("""
                INSERT INTO horse_stay (booking_horse_id, location_id, hub_stall_id, stay_type,
                    planned_start_at, planned_end_at, actual_start_at)
                VALUES (?, ?, ?, 'ASSEMBLY', now(), now() + INTERVAL '1 day', now())
                RETURNING id
                """, Long.class, horse, hub, stall);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }
}
