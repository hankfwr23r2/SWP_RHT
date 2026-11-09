package com.example.racehorse_transport;

import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.RollbackException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/** Exercises actual identity/composite-key cascades against the migrated PostgreSQL schema. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BookingRelationalPersistenceTest {
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
    private static final AtomicInteger KEYS = new AtomicInteger(88000);
    static { POSTGRES.start(); }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private BookingRepository bookings;
    @Autowired private HorseRepository horses;
    @Autowired private ServiceRepository services;
    @Autowired private CategoryRepository categories;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private EntityManagerFactory entityManagers;
    @Autowired private JdbcTemplate sql;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void identityBookingCascadesAndReloadsStableSelectionsPoliciesAndOrphanCleanup() throws Exception {
        int id = createBooking();
        assertThat(count("booking_horse", id)).isEqualTo(1);
        assertThat(count("service_booking", id)).isEqualTo(1);
        assertThat(count("booking_horse_insurance", id)).isEqualTo(1);
        long policyId = sql.queryForObject("select insurance_id from booking_horse_insurance where bookingid=?", Long.class, id);
        long documentId = sql.queryForObject("select document_id from insurance_document where insurance_id=?", Long.class, policyId);
        assertThat(json.readTree(sql.queryForObject("select details_json from booking where bookingid=?", String.class, id)).has("horses"))
                .isFalse();

        transaction().executeWithoutResult(status -> {
            Booking booking = bookings.findById(id).orElseThrow();
            JsonNode payload = parse(booking.getDetailsJson());
            assertThat(payload.path("horses").path(0).path("insurance").path("policyNumber").asText()).isEqualTo("POLICY-ORIGINAL");
            ((ObjectNode) payload.path("horses").path(0).path("insurance")).put("provider", "Updated insurer");
            ((ObjectNode) payload).put("gate", "Updated gate");
            booking.setDetailsJson(payload.toString());
            bookings.flush();
        });

        assertThat(count("booking_horse", id)).isEqualTo(1);
        assertThat(count("service_booking", id)).isEqualTo(1);
        assertThat(sql.queryForObject("select insurance_id from booking_horse_insurance where bookingid=?", Long.class, id)).isEqualTo(policyId);
        assertThat(sql.queryForObject("select document_id from insurance_document where insurance_id=?", Long.class, policyId)).isEqualTo(documentId);
        assertThat(sql.queryForObject("select unit_price from service_booking where bookingid=?", BigDecimal.class, id)).isEqualByComparingTo("0");
        assertThat(sql.queryForObject("select provider_name from booking_horse_insurance where bookingid=?", String.class, id)).isEqualTo("Updated insurer");

        transaction().executeWithoutResult(status -> {
            Booking booking = bookings.findById(id).orElseThrow();
            booking.setDetailsJson("{\"horses\":[],\"history\":[]}");
            bookings.flush();
        });
        assertThat(count("booking_horse", id)).isZero();
        assertThat(count("service_booking", id)).isZero();
        assertThat(count("booking_horse_insurance", id)).isZero();
        assertThat(sql.queryForObject("select count(*) from insurance_document where insurance_id=?", Integer.class, policyId)).isZero();
    }

    @Test
    void staleConcurrentBookingWriteIsRejected() {
        int id = createBooking();
        try (EntityManager first = entityManagers.createEntityManager(); EntityManager second = entityManagers.createEntityManager()) {
            first.getTransaction().begin(); second.getTransaction().begin();
            Booking a = first.find(Booking.class, id);
            Booking b = second.find(Booking.class, id);
            a.setStatus("under_review"); b.setStatus("cancelled");
            first.getTransaction().commit();
            assertThatThrownBy(() -> second.getTransaction().commit()).isInstanceOf(RollbackException.class);
        }
        assertThat(bookings.findById(id).orElseThrow().getStatus()).isEqualTo("under_review");
    }

    private int createBooking() {
        return transaction().execute(status -> {
            int key = KEYS.incrementAndGet();
            Category category = new Category(); category.setId(key); category.setCategoryName("Mapping fixtures");
            category = categories.saveAndFlush(category);
            Service service = new Service(); service.setId(key); service.setCategoryID(category);
            service.setServiceName("Free snapshot service"); service.setServiceCode("MAPPING_" + key);
            service.setBillingUnit("PER_HORSE"); service.setPrice(BigDecimal.ZERO); service.setStatus("ACTIVE");
            services.saveAndFlush(service);
            Horse horse = horses.saveAndFlush(Horse.builder().horseName("Horse " + key).completedTrips(0).build());
            String details = """
                    {"originCountry":"VN","destCountry":"VN","horses":[{"horseId":"H-%s","name":"Snapshot horse",
                     "selectedServices":{"feed":{"id":%s,"serviceCode":"MAPPING_%s","serviceName":"Free snapshot service","price":0}},
                     "selectedServiceCodes":{"feed":"MAPPING_%s"},
                     "insurance":{"option":"personal","provider":"Original insurer","policyNumber":"POLICY-ORIGINAL",
                     "documents":["https://example.test/policy.pdf"],"policyDocuments":[],"documentNames":{"https://example.test/policy.pdf":"Original.pdf"}}}],
                     "quote":{"insuranceSnapshot":[{"provider":"Frozen insurer"}]},"history":[]}
                    """.formatted(horse.getId(), key, key, key);
            // Deliberately use the builder: @PrePersist must handle identity parents correctly.
            return bookings.saveAndFlush(Booking.builder().status("pending_intake").detailsJson(details).build()).getId();
        });
    }

    private TransactionTemplate transaction() { return new TransactionTemplate(transactions); }

    private int count(String table, int bookingId) {
        return sql.queryForObject("select count(*) from " + table + " where bookingid=?", Integer.class, bookingId);
    }

    private JsonNode parse(String value) {
        try { return json.readTree(value); }
        catch (Exception exception) { throw new AssertionError(exception); }
    }
}
