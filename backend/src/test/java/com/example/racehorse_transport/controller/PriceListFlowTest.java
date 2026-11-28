package com.example.racehorse_transport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.dto.pricing.PriceListCreateRequestDto;
import com.example.racehorse_transport.entity.enums.PriceListStatus;
import com.example.racehorse_transport.exception.PricingRuleException;
import com.example.racehorse_transport.repository.BillableServiceRepository;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.service.PriceListService;
import com.example.racehorse_transport.support.TestData;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@PostgresIntegrationTest
@AutoConfigureMockMvc
class PriceListFlowTest {
    @Autowired private MockMvc mvc;
    @Autowired private TestData data;
    @Autowired private JwtUtils jwt;
    @Autowired private PriceListService service;
    @Autowired private BillableServiceRepository billableServices;

    @Test
    @Transactional
    void adminCanCreateDraftAndCustomerCannotListPrices() throws Exception {
        String admin = data.unique("pricing-admin");
        data.staff(admin, "ADMIN");
        String customer = data.unique("pricing-customer");
        data.customer(customer);
        mvc.perform(get("/price-lists").header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + jwt.generateToken(customer, List.of("CUSTOMER"), 0)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/price-lists").header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + jwt.generateToken(admin, List.of("ADMIN"), 0))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"A3 test","currencyCode":"VND","marginRate":0.1,"fuelBufferRate":0.05,
                                 "depositRate":0.30,"validFrom":"2090-01-01"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    @Transactional
    void customerCanListBillableServicesButCannotListPriceLists() throws Exception {
        String customer = data.unique("catalog-customer");
        data.customer(customer);
        String authorization = "Bearer " + jwt.generateToken(customer, List.of("CUSTOMER"), 0);
        mvc.perform(get("/billable-services").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
        mvc.perform(get("/price-lists").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    void adminCanPersistAnOpenEndedKilometerTier() throws Exception {
        String admin = data.unique("pricing-tier-admin");
        data.staff(admin, "ADMIN");
        Long listId = service.create(new PriceListCreateRequestDto("Open tier", "VND", BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, LocalDate.of(2090, 1, 1), null)).id();
        Long directId = billableServices.findByCode("DIRECT_KM").orElseThrow().getId();
        mvc.perform(post("/price-lists/{id}/items", listId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.generateToken(admin, List.of("ADMIN"), 0))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"billableServiceId":%d,"kmFrom":200,"unitPrice":20000}
                                """.formatted(directId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.kmFrom").value(200))
                .andExpect(jsonPath("$.data.kmTo").doesNotExist());
        assertThat(service.items(listId)).singleElement().satisfies(item -> {
            assertThat(item.kmFrom()).isEqualByComparingTo("200");
            assertThat(item.kmTo()).isNull();
        });
    }

    @Test
    void concurrentActivationsForSameCurrencyAndDateYieldOneConflict() throws Exception {
        var request = new PriceListCreateRequestDto("A3 concurrency", "XTS", BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, LocalDate.of(2090, 1, 1), null);
        Long first = service.create(request).id();
        Long second = service.create(request).id();
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var firstResult = pool.submit(() -> activateAfter(start, first));
            var secondResult = pool.submit(() -> activateAfter(start, second));
            start.countDown();
            assertThat(List.of(firstResult.get(10, TimeUnit.SECONDS), secondResult.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            for (Long id : List.of(first, second)) {
                if (service.get(id).status() == PriceListStatus.ACTIVE) service.archive(id);
                else service.delete(id);
            }
        }
    }

    private boolean activateAfter(CountDownLatch start, Long id) throws InterruptedException {
        start.await();
        try {
            service.activate(id);
            return true;
        } catch (PricingRuleException error) {
            assertThat(error.isConflict()).isTrue();
            return false;
        }
    }
}
