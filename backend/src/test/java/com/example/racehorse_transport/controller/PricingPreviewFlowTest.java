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
import org.springframework.transaction.annotation.Transactional;

@PostgresIntegrationTest
@AutoConfigureMockMvc
class PricingPreviewFlowTest {
    @Autowired private MockMvc mvc;
    @Autowired private TestData data;
    @Autowired private JwtUtils jwt;
    @Autowired private JdbcTemplate jdbc;
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
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.generateToken(customer, List.of("CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/pricing/preview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.generateToken(manager, List.of("MANAGER")))
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
        when(routing.truckRoute(anyList(), eq(10.0), eq(60), any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            if (TransactionSynchronizationManager.getResource(entityManagerFactory) instanceof EntityManagerHolder holder) {
                var session = holder.getEntityManager().unwrap(SessionImplementor.class);
                assertThat(session.getJdbcCoordinator().getLogicalConnection().isPhysicallyConnected()).isFalse();
            }
            return new RoadRouteDto(35, 600, List.of());
        });
        String token = "Bearer " + jwt.generateToken(customer, List.of("CUSTOMER"));
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

    private Long insertAddress(Long owner, String latitude) {
        return jdbc.queryForObject("""
                INSERT INTO customer_address (customer_id, label, address_line, country_code, province_code, latitude, longitude)
                VALUES (?, 'Pricing test', 'Pricing test address', 'VN', 'VN-79', CAST(? AS numeric), 106.70)
                RETURNING id
                """, Long.class, owner, latitude);
    }
}
