package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.admin.UpdateSystemConfigRequest;
import com.example.racehorse_transport.entity.SystemLog;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.repository.SystemLogRepository;
import com.example.racehorse_transport.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static com.example.racehorse_transport.service.AdminAuditWriter.values;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class AdminAuditIntegrationTest {
    @Autowired AdminAuditWriter writer;
    @Autowired AdminSystemLogService reader;
    @Autowired SystemConfigService configs;
    @Autowired UserRepository users;
    @Autowired SystemLogRepository logs;
    @Autowired PlatformTransactionManager transactions;
    @Autowired AdminAccountService accounts;
    @Autowired com.example.racehorse_transport.repository.StaffRepository staff;
    @Autowired com.example.racehorse_transport.repository.LocationRepository locations;
    @Autowired RouteService routes;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager entities;

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void queriesRealDatabaseWithScopeEscapingExactKeysAndInclusiveStartExclusiveEnd() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            User actor = new User(); actor.setUsername("Audit%_" + UUID.randomUUID()); actor.setEmail(actor.getUsername() + "@example.com");
            users.saveAndFlush(actor);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    actor.getUsername(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            Instant time = Instant.parse("2026-01-02T00:00:00Z");
            writer.record("UPDATE_ACCOUNT", "USER", 7, "Target", "Change", values("name", "Old"), values("name", "New"));
            var entry = reader.search("USER", null, actor.getUsername(), "7", null, null, 0, 20).content().getFirst();
            var persisted = logs.findById(entry.id()).orElseThrow(); persisted.setCreatedAt(time); logs.saveAndFlush(persisted);
            logs.saveAndFlush(SystemLog.builder().targetTable("USER").targetRecordID(17).actionType("UPDATE_ACCOUNT")
                    .actionbyUserid(actor).createdAt(time).build());
            logs.saveAndFlush(SystemLog.builder().targetTable("BOOKING").targetRecordID(7).actionType("CREATE_BOOKING")
                    .actionbyUserid(actor).createdAt(time).build());

            var found = reader.search(null, null, actor.getUsername().toUpperCase(), "7", time, time.plusSeconds(1), 0, 20);
            assertThat(found.totalElements()).isEqualTo(1);
            assertThat(found.content().getFirst().id()).isEqualTo(entry.id());
            assertThat(reader.search(null, null, actor.getUsername(), "7", time.minusSeconds(1), time, 0, 20).content()).isEmpty();
            assertThat(reader.search(null, null, "missing%_" + actor.getUsername(), null, null, null, 0, 20).content()).isEmpty();
            assertThat(reader.detail(entry.id()).legacy()).isFalse();
            tx.setRollbackOnly();
        });
    }

    @Test
    void failureToCaptureActorRollsBackConfigurationChange() {
        String key = "TRIP_OPEN_FEE";
        String original = configs.getConfig(key).getConfigValue();
        UpdateSystemConfigRequest request = new UpdateSystemConfigRequest();
        request.setConfigValue("3500001".equals(original) ? "3500002" : "3500001");
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> configs.updateConfig(key, request, "forged-name")).isInstanceOf(AccessDeniedException.class);
        assertThat(configs.getConfig(key).getConfigValue()).isEqualTo(original);
    }

    @Test
    void administratorEditingOwnIdentityKeepsOriginalActorAndCorrectBeforeAfter() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            User user = new User(); user.setUsername("Admin-" + UUID.randomUUID()); user.setEmail(user.getUsername() + "@example.com");
            users.saveAndFlush(user);
            var person = new com.example.racehorse_transport.entity.Staff();
            person.setUser(user); person.setRole("ADMIN"); person.setPhone("0901234567"); person.setEmploymentStatus("ACTIVE");
            staff.saveAndFlush(person);
            String originalName = user.getUsername();
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    originalName, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            var request = com.example.racehorse_transport.dto.admin.UpdateAccountRequest.builder()
                    .name("Updated-" + UUID.randomUUID()).email("changed-" + UUID.randomUUID() + "@example.com")
                    .phone("0901234568").build();
            accounts.updateAccount(user.getId(), request, "forged-actor");
            var result = reader.search("USER", "UPDATE_ACCOUNT", originalName, String.valueOf(user.getId()), null, null, 0, 20, "ADMIN");
            assertThat(result.totalElements()).isEqualTo(1);
            var entry = result.content().getFirst();
            assertThat(entry.actorName()).isEqualTo(originalName);
            assertThat(entry.actorRole()).isEqualTo("ADMIN");
            assertThat(entry.oldData()).contains(originalName, "0901234567");
            assertThat(entry.newData()).contains(request.getName(), request.getEmail(), "0901234568");
            assertThat(entry.description()).isEqualTo("Cập nhật thông tin tài khoản");
            tx.setRollbackOnly();
        });
    }

    @Test
    void routeSnapshotDoesNotInterfereWithReplacingStopsWithSameIds() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            User actor = new User(); actor.setUsername("RouteAdmin-" + UUID.randomUUID()); actor.setEmail(actor.getUsername() + "@example.com");
            users.saveAndFlush(actor);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    actor.getUsername(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            int start = locations.findMaxId() + 1;
            for (int i = 0; i < 2; i++) {
                var location = new com.example.racehorse_transport.entity.Location();
                location.setId(start + i); location.setLocationName("Audit stop " + i); location.setLocationType("Stable");
                location.setAddress("Address"); location.setCity("City"); location.setProvince("Province"); location.setCountry("VN");
                locations.saveAndFlush(location);
            }
            var request = com.example.racehorse_transport.dto.route.CreateRouteRequest.builder().routeName("Audit route")
                    .stops(List.of(stop(start, 1), stop(start + 1, 2))).build();
            var route = routes.createRoute(request);
            entities.flush();
            entities.clear(); // A subsequent HTTP request starts with a fresh persistence context.
            request.setStops(List.of(stop(start + 1, 1), stop(start, 2)));
            routes.updateRoute(route.getId(), request);
            var result = reader.search("ROUTE", "UPDATE_ROUTE", actor.getUsername(), String.valueOf(route.getId()), null, null, 0, 20);
            assertThat(result.totalElements()).isEqualTo(1);
            assertThat(result.content().getFirst().oldData()).contains("Audit stop 0");
            assertThat(routes.getStopsByRouteId(route.getId()).getFirst().getLocationID().getId()).isEqualTo(start + 1);
            tx.setRollbackOnly();
        });
    }

    private com.example.racehorse_transport.dto.route.CreateRouteRequest.RouteStopItem stop(int id, int order) {
        return com.example.racehorse_transport.dto.route.CreateRouteRequest.RouteStopItem.builder()
                .locationId(id).stopOrder(order).latitude(10.0 + order).longitude(106.0 + order).build();
    }
}
