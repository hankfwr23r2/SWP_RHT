package com.example.racehorse_transport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.client.ValhallaClient;
import com.example.racehorse_transport.dto.route.RoadRouteDto;
import com.example.racehorse_transport.routing.RoutingStubServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.engine.spi.SessionImplementor;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.support.TestData;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@PostgresIntegrationTest
@AutoConfigureMockMvc
class PricingPreviewFlowTest {
    @Autowired private MockMvc mvc;
    @Autowired private TestData data;
    @Autowired private JwtUtils jwt;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Clock clock;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @MockitoBean private ValhallaClient routing;

    @Test
    @Transactional
    void validatesCustomerPayloadAndRejectsManager() throws Exception {
        String customer = data.unique("preview-customer");
        data.customer(customer);
        String manager = data.unique("preview-manager");
        data.staff(manager, "MANAGER");
        mvc.perform(post("/pricing/preview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.generateToken(customer, List.of("CUSTOMER"), 0))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/pricing/preview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.generateToken(manager, List.of("MANAGER"), 0))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"pickupAddressId":1,"deliveryAddressId":2,"pickupAt":"2090-01-01T00:00:00Z",
                                 "horses":[{"horseId":1,"stallTypeCode":"STANDARD","addOnServiceIds":[]}]}
                                """))
                .andExpect(status().isForbidden());
    }
    @Test
    void previewsWideStallWithDatabaseConfigAndRejectsRetiredFeeding() throws Exception {
        String customer = "customer1.dev";
        Long owner = jdbc.queryForObject("SELECT id FROM app_user WHERE username = ?", Long.class, customer);
        Long horse = jdbc.queryForObject("SELECT id FROM horse WHERE microchip = '985000000100001'", Long.class);
        Long feeding = jdbc.queryForObject("SELECT id FROM billable_service WHERE code = 'FEEDING'", Long.class);
        Long pickup = insertAddress(owner, "10.70");
        Long delivery = insertAddress(owner, "10.80");
        when(routing.truckRoute(anyList(), eq(10.0), eq(80), any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            if (TransactionSynchronizationManager.getResource(entityManagerFactory) instanceof EntityManagerHolder holder) {
                var session = holder.getEntityManager().unwrap(SessionImplementor.class);
                assertThat(session.getJdbcCoordinator().getLogicalConnection().isPhysicallyConnected()).isFalse();
            }
            return new RoadRouteDto(35, 600, List.of());
        });
        String token = "Bearer " + jwt.generateToken(customer, List.of("CUSTOMER"), 0);
        String payload = """
                {"pickupAddressId":%d,"deliveryAddressId":%d,"pickupAt":"2090-01-01T00:00:00Z",
                 "horses":[{"horseId":%d,"stallTypeCode":"WIDE","addOnServiceIds":[%s]}]}
                """;
        try {
            mvc.perform(post("/pricing/preview").header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON).content(payload.formatted(pickup, delivery, horse, "")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.legs[0].layer").value("DIRECT"))
                    .andExpect(jsonPath("$.data.lines[?(@.serviceCode == 'STALL_WIDE')].quantity").value(org.hamcrest.Matchers.contains(1)));
            mvc.perform(post("/pricing/preview").header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload.formatted(pickup, delivery, horse, feeding)))
                    .andExpect(status().isBadRequest());
        } finally {
            jdbc.update("DELETE FROM customer_address WHERE id IN (?, ?)", pickup, delivery);
        }
    }

    @Test
    void rejectsLongCompanyPickupAndOmitsRoadLegsForSelfTransfer() throws Exception {
        String customer = "customer1.dev";
        Long owner = jdbc.queryForObject("SELECT id FROM app_user WHERE username = ?", Long.class, customer);
        Long horse = jdbc.queryForObject("SELECT id FROM horse WHERE microchip = '985000000100001'", Long.class);
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDate pickupDate = LocalDate.ofInstant(clock.instant(), zone).plusDays(30);
        var departure = data.departure("HCM-DN", pickupDate.plusDays(2), 4);
        Long pickup = insertAddress(owner, "VN-79", "10.70");
        Long delivery = insertAddress(owner, "VN-48", "16.90");
        String token = "Bearer " + jwt.generateToken(customer, List.of("CUSTOMER"), 0);
        String payload = """
                {"pickupAddressId":%d,"deliveryAddressId":%d,"departureId":%d,"pickupAt":"%s",
                 "pickupMethod":"%s","deliveryMethod":"SELF",
                 "horses":[{"horseId":%d,"stallTypeCode":"STANDARD","addOnServiceIds":[]}]}
                """;
        var pickupAt = pickupDate.atTime(12, 0).atZone(zone).toInstant();
        try (var stub = new RoutingStubServer()) {
            stub.replyRaw("/route", 200, """
                    {"trip":{"summary":{"length":120,"time":18001},
                             "legs":[{"summary":{"length":120,"time":18001},"shape":""}]}}
                    """);
            var client = new ValhallaClient(stub.url(), new ObjectMapper());
            when(routing.truckRoute(anyList(), anyDouble(), anyInt(), any()))
                    .thenAnswer(invocation -> client.truckRoute(invocation.getArgument(0),
                            invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3)));

            mvc.perform(post("/pricing/preview").header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload.formatted(pickup, delivery, departure.getId(), pickupAt, "COMPANY", horse)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Chặng đón")));
            mvc.perform(post("/pricing/preview").header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload.formatted(pickup, delivery, departure.getId(), pickupAt, "SELF", horse)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.legs[?(@.layer != 'LINEHAUL')]").isEmpty())
                    .andExpect(jsonPath("$.data.lines[?(@.serviceCode == 'PICKUP_KM')]").isEmpty())
                    .andExpect(jsonPath("$.data.lines[?(@.serviceCode == 'DELIVERY_KM')]").isEmpty())
                    .andExpect(jsonPath("$.data.lines[?(@.serviceCode == 'QUARANTINE_STAY')].quantity")
                            .value(org.hamcrest.Matchers.contains(1)));
            assertThat(stub.requests("/route")).hasSize(1);
        } finally {
            jdbc.update("DELETE FROM customer_address WHERE id IN (?, ?)", pickup, delivery);
            jdbc.update("DELETE FROM departure WHERE id = ?", departure.getId());
        }
    }

    @Test
    void customerChecksCompanyTransferForOwnAddressesOnly() throws Exception {
        String customer = "customer1.dev";
        Long owner = jdbc.queryForObject("SELECT id FROM app_user WHERE username = ?", Long.class, customer);
        Long pickup = insertAddress(owner, "VN-79", "10.70");
        Long delivery = insertAddress(owner, "VN-48", "16.90");
        String payload = """
                {"pickupAddressId":%d,"deliveryAddressId":%d,"pickupAt":"2090-01-01T00:00:00Z"}
                """.formatted(pickup, delivery);
        when(routing.truckRoute(anyList(), anyDouble(), anyInt(), any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new RoadRouteDto(400, 18001, List.of());
        }).thenReturn(new RoadRouteDto(20, 1800, List.of()));
        try {
            transferOptions(customer, "CUSTOMER", payload).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.pickup.companyAllowed").value(false))
                    .andExpect(jsonPath("$.data.pickup.status").value("TOO_FAR"))
                    .andExpect(jsonPath("$.data.pickup.reason").value(org.hamcrest.Matchers.containsString("tự chở")))
                    .andExpect(jsonPath("$.data.delivery.companyAllowed").value(true))
                    .andExpect(jsonPath("$.data.delivery.status").value("AVAILABLE"));
            String other = data.unique("transfer-other");
            data.customer(other);
            transferOptions(other, "CUSTOMER", payload).andExpect(status().isNotFound());
            transferOptions("coordinator.dev", "COORDINATOR", payload).andExpect(status().isForbidden());
            transferOptions(customer, "CUSTOMER", "{}").andExpect(status().isBadRequest());
            mvc.perform(post("/pricing/transfer-options").contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isUnauthorized());
        } finally {
            jdbc.update("DELETE FROM customer_address WHERE id IN (?, ?)", pickup, delivery);
        }
    }

    private ResultActions transferOptions(String username, String role, String payload) throws Exception {
        return mvc.perform(post("/pricing/transfer-options").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.generateToken(username, List.of(role), 0))
                .contentType(MediaType.APPLICATION_JSON).content(payload));
    }

    private Long insertAddress(Long owner, String latitude) {
        return insertAddress(owner, "VN-79", latitude);
    }

    private Long insertAddress(Long owner, String province, String latitude) {
        return jdbc.queryForObject("""
                INSERT INTO customer_address (customer_id, label, address_line, country_code, province_code, latitude, longitude)
                VALUES (?, 'Pricing test', 'Pricing test address', 'VN', ?, CAST(? AS numeric), 106.70)
                RETURNING id
                """, Long.class, owner, province, latitude);
    }
}
