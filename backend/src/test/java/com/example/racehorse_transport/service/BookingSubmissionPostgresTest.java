package com.example.racehorse_transport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto;
import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto.AddOnInput;
import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto.AddressInput;
import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto.HorseInput;
import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto.PartyInput;
import com.example.racehorse_transport.dto.booking.BookingResponseDto;
import com.example.racehorse_transport.dto.booking.InsuranceDeclarationRequestDto;
import com.example.racehorse_transport.entity.enums.*;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.support.TestData;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@PostgresIntegrationTest
@AutoConfigureMockMvc
class BookingSubmissionPostgresTest {

    @Autowired private BookingService bookings;
    @Autowired private BookingQueryService queries;
    @Autowired private TestData data;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Clock clock;
    @Autowired private JwtUtils jwt;
    @Autowired private MockMvc mvc;

    @Test
    void persistsAllRowsAndReservesStalls() {
        String owner = customer();
        LocalDate date = pickupDate();
        var departure = data.departure("HCM-DN", date, 4);
        Long addOn = jdbc.queryForObject("""
                INSERT INTO billable_service (code, name, layer, billing_unit, status)
                VALUES (?, 'Test add-on', 'ADD_ON', 'PER_BOOKING', 'ACTIVE') RETURNING id
                """, Long.class, data.unique("A52"));
        var request = request(departure.getId(), date, List.of(
                horseInput(horse(owner), InsuranceCoverageOption.NONE, true, List.of(new AddOnInput(addOn, 2))),
                horseInput(horse(owner), InsuranceCoverageOption.NONE, true, List.of())));
        BookingResponseDto result = bookings.submit(owner, "192.0.2.5", request);
        Long id = result.id();

        assertThat(result.status()).isEqualTo(BookingStatus.PENDING_INTAKE);
        assertThat(result.emergencySpendingLimit()).isEqualByComparingTo("1000");
        assertThat(count("SELECT count(*) FROM booking_party WHERE booking_id = ?", id)).isEqualTo(4);
        assertThat(count("SELECT count(*) FROM booking_address WHERE booking_id = ?", id)).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM booking_horse WHERE booking_id = ? AND status = 'ACTIVE'", id)).isEqualTo(2);
        assertThat(count("""
                SELECT count(*) FROM booking_horse_service s JOIN booking_horse h ON h.id = s.booking_horse_id
                WHERE h.booking_id = ? AND s.quantity = 2
                """, id)).isEqualTo(1);
        assertThat(count("""
                SELECT count(DISTINCT i.waiver_terms_acceptance_id) FROM horse_insurance i
                JOIN booking_horse h ON h.id = i.booking_horse_id
                JOIN terms_acceptance t ON t.id = i.waiver_terms_acceptance_id AND t.booking_id = h.booking_id
                WHERE h.booking_id = ? AND i.status = 'DRAFT' AND t.terms_type = 'LIABILITY_WAIVER'
                """, id)).isEqualTo(2);
        assertThat(count("""
                SELECT count(*) FROM terms_acceptance WHERE booking_id = ? AND terms_type = 'SERVICE_TERMS'
                AND ip_address = '192.0.2.5'
                """, id)).isEqualTo(1);
        assertThat(count("""
                SELECT count(*) FROM booking_status_history WHERE booking_id = ?
                AND from_status IS NULL AND to_status = 'PENDING_INTAKE'
                """, id)).isEqualTo(1);
        assertThat(reserved(departure.getId())).isEqualTo(2);
        assertThat(queries.get(owner, id).horses()).hasSize(2);
    }

    @Test
    void fullDepartureRollsBackEverything() {
        String owner = customer();
        LocalDate date = pickupDate();
        var departure = data.departure("HCM-DN", date, 1);
        jdbc.update("UPDATE departure SET booked_stall_count = 1 WHERE id = ?", departure.getId());
        Long horse = horse(owner);
        Long customer = jdbc.queryForObject("SELECT id FROM app_user WHERE username = ?", Long.class, owner);
        var request = request(departure.getId(), date,
                List.of(horseInput(horse, InsuranceCoverageOption.NONE, true, List.of())));

        assertThatThrownBy(() -> bookings.submit(owner, "192.0.2.5", request)).isInstanceOf(IllegalStateException.class);
        assertThat(queries.list(owner)).isEmpty();
        assertThat(count("SELECT count(*) FROM booking_horse WHERE horse_id = ?", horse)).isZero();
        assertThat(count("SELECT count(*) FROM terms_acceptance WHERE user_id = ?", customer)).isZero();
        assertThat(count("SELECT count(*) FROM booking_status_history WHERE changed_by = ?", customer)).isZero();
        assertThat(reserved(departure.getId())).isEqualTo(1);
    }

    @Test
    void directWithoutDeparturePersistsAnUnpricedArrangedDraftAndHorseBecomesBusy() {
        String owner = customer();
        var request = directArranged(owner);
        BookingResponseDto result = bookings.submit(owner, null, request);
        assertThat(result.departureId()).isNull();
        assertThat(count("""
                SELECT count(*) FROM horse_insurance i JOIN booking_horse h ON h.id = i.booking_horse_id
                WHERE h.booking_id = ? AND i.coverage_option = 'ARRANGED' AND i.status = 'DRAFT'
                AND i.insurer_party_id IS NULL AND i.coverage_amount IS NULL AND i.policy_number IS NULL
                """, result.id())).isEqualTo(1);
        assertThatThrownBy(() -> bookings.submit(owner, null, request))
                .isInstanceOfSatisfying(BookingRuleException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(409);
                    assertThat(ex.getReason()).contains(result.code());
                });
    }

    @Test
    void refusesNoneWithoutWaiver() {
        String owner = customer();
        var request = request(null, pickupDate(), List.of(horseInput(horse(owner),
                InsuranceCoverageOption.NONE, false, List.of())));
        assertThatThrownBy(() -> bookings.submit(owner, null, request))
                .isInstanceOf(BookingRuleException.class).hasMessageContaining("miễn trách");
        assertThat(queries.list(owner)).isEmpty();
    }

    @Test
    void onlyOneOfTwoConcurrentSubmissionsForTheSameHorseSucceeds() throws Exception {
        String owner = customer();
        var request = directArranged(owner);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var work = (java.util.concurrent.Callable<Integer>) () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent booking test did not start.");
                }
                try {
                    bookings.submit(owner, null, request);
                    return 201;
                } catch (BookingRuleException ex) {
                    return ex.getStatusCode().value();
                }
            };
            var first = executor.submit(work);
            var second = executor.submit(work);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(queries.list(owner)).hasSize(1);
    }

    @Test
    void apiScopesReadsRejectsNonCustomersAndIgnoresClientStatusAndPrice() throws Exception {
        String owner = customer();
        String other = customer();
        String staff = data.unique("a52-admin");
        data.staff(staff, "ADMIN");
        var mapper = new ObjectMapper().findAndRegisterModules();
        var body = mapper.valueToTree(directArranged(owner));
        var object = (com.fasterxml.jackson.databind.node.ObjectNode) body;
        object.put("status", "COMPLETED");
        object.put("price", 42);
        var insurance = (com.fasterxml.jackson.databind.node.ObjectNode) object.at("/horses/0/insurance");
        insurance.put("coverageAmount", 1234);
        insurance.put("status", "CONFIRMED");
        String payload = mapper.writeValueAsString(object);

        mvc.perform(get("/bookings")).andExpect(status().isUnauthorized());
        mvc.perform(get("/bookings").header(HttpHeaders.AUTHORIZATION, token(staff, "ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/bookings").header(HttpHeaders.AUTHORIZATION, token(staff, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isForbidden());
        mvc.perform(post("/bookings").header(HttpHeaders.AUTHORIZATION, token(owner, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("PENDING_INTAKE"))
                .andExpect(jsonPath("$.data.price").doesNotExist());
        Long id = queries.list(owner).getFirst().id();
        assertThat(count("""
                SELECT count(*) FROM horse_insurance i JOIN booking_horse h ON h.id = i.booking_horse_id
                WHERE h.booking_id = ? AND i.coverage_amount IS NULL
                """, id)).isEqualTo(1);
        mvc.perform(get("/bookings").header(HttpHeaders.AUTHORIZATION, token(owner, "CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(id.intValue()));
        mvc.perform(get("/bookings").header(HttpHeaders.AUTHORIZATION, token(other, "CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/bookings/{id}", id).header(HttpHeaders.AUTHORIZATION, token(other, "CUSTOMER")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/bookings/{id}", id).header(HttpHeaders.AUTHORIZATION, token(staff, "ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/bookings/{id}", id).header(HttpHeaders.AUTHORIZATION, token(owner, "CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.horses[0].insurance.status").value("DRAFT"));
    }

    private String customer() {
        String username = data.unique("a52-customer");
        data.customer(username);
        return username;
    }

    private Long horse(String owner) {
        return jdbc.queryForObject("""
                INSERT INTO horse (owner_customer_id, name, sex, horse_type_id, status)
                SELECT u.id, ?, 'MARE', (SELECT min(id) FROM horse_type), 'ACTIVE' FROM app_user u WHERE u.username = ?
                RETURNING id
                """, Long.class, data.unique("a52-horse"), owner);
    }

    private LocalDate pickupDate() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(30);
    }

    private BookingCreateRequestDto directArranged(String owner) {
        return request(null, pickupDate(), List.of(horseInput(horse(owner), InsuranceCoverageOption.ARRANGED,
                false, List.of())));
    }

    private BookingCreateRequestDto request(Long departureId, LocalDate date, List<HorseInput> horses) {
        var parties = Arrays.stream(BookingPartyRole.values())
                .map(role -> new PartyInput(role, role.name(), "0901234567", null, null, null, "Test address"))
                .toList();
        return new BookingCreateRequestDto(departureId == null ? BookingType.LOCAL : BookingType.INTERPROVINCE,
                departureId, date, new BigDecimal("1000"), parties,
                List.of(address(AddressRole.PICKUP, "VN-79"),
                        address(AddressRole.DELIVERY, departureId == null ? "VN-79" : "VN-48")), horses, true);
    }

    private AddressInput address(AddressRole role, String province) {
        return new AddressInput(role, "Test address", province, "VN", new BigDecimal("10.8"), new BigDecimal("106.8"),
                "Test contact", "0901234567", null, null);
    }

    private HorseInput horseInput(Long id, InsuranceCoverageOption option, boolean waiver, List<AddOnInput> addOns) {
        return new HorseInput(id, "STANDARD", "Hay", (short) 2, true, addOns,
                new InsuranceDeclarationRequestDto(option, "Ignored", null, null, 999999L, waiver));
    }

    private int count(String sql, Long id) {
        return jdbc.queryForObject(sql, Integer.class, id);
    }

    private int reserved(Long id) {
        return jdbc.queryForObject("SELECT booked_stall_count FROM departure WHERE id = ?", Integer.class, id);
    }

    private String token(String username, String role) {
        return "Bearer " + jwt.generateToken(username, List.of(role));
    }
}
