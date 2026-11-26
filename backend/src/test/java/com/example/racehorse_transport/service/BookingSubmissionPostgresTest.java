package com.example.racehorse_transport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@PostgresIntegrationTest
@AutoConfigureMockMvc
class BookingSubmissionPostgresTest {

    @Autowired private AccountService accounts;
    @Autowired private BookingService bookings;
    @Autowired private BookingQueryService queries;
    @Autowired private BookingCancellationService cancellations;
    @Autowired private BookingReviewService review;
    @Autowired private InsuranceService insurance;
    @Autowired private DocumentReviewService documentReview;
    @Autowired private DocumentCompletenessService documentCompleteness;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.example.racehorse_transport.client.CloudinaryClient cloudinary;
    @Autowired private TestData data;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Clock clock;
    @Autowired private JwtUtils jwt;
    @Autowired private MockMvc mvc;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private DeliveryRouteService deliveryRoutes;

    @Test
    void savesTheDeliveryAddressOfEachHorseAndRejectsAnAddressFromAnotherBooking() {
        String owner = customer();
        LocalDate date = pickupDate();
        var departure = data.departure("HCM-DN", date.plusDays(1), 4);
        HorseInput first = horseInput(horse(owner), InsuranceCoverageOption.NONE, true, List.of());
        HorseInput other = horseInput(horse(owner), InsuranceCoverageOption.NONE, true, List.of());
        HorseInput second = new HorseInput(other.horseId(), other.stallTypeCode(), other.dietNote(), other.feedingsPerDay(),
                other.feedSuppliedByOwner(), other.addOns(), other.insurance(), 1);
        var original = request(departure.getId(), date, List.of(first, second));
        var primary = original.addresses().stream().filter(address -> address.addressType() == AddressRole.DELIVERY)
                .findFirst().orElseThrow();
        var additional = new AddressInput(AddressRole.DELIVERY, "Điểm giao thứ hai", primary.provinceCode(), primary.countryCode(),
                primary.latitude().add(new BigDecimal("0.01")), primary.longitude(), "Người nhận thứ hai", "0912345678", null, null);
        var addresses = new java.util.ArrayList<>(original.addresses());
        addresses.add(additional);
        var request = new BookingCreateRequestDto(original.bookingType(), original.departureId(), date,
                original.emergencySpendingLimit(), original.parties(), addresses, original.horses(), true);

        var result = bookings.submit(owner, "192.0.2.5", request);

        var detail = queries.get(owner, result.id());
        assertThat(detail.horses()).extracting(horse -> detail.addresses().stream()
                        .filter(address -> address.id().equals(horse.deliveryAddressId())).findFirst().orElseThrow().addressLine())
                .containsExactly(primary.addressLine(), additional.addressLine());
        assertThat(detail.addresses()).hasSize(3);
        var otherBooking = bookings.submit(owner, "192.0.2.5", request(departure.getId(), date,
                List.of(horseInput(horse(owner), InsuranceCoverageOption.NONE, true, List.of()))));
        Long otherAddressId = queries.get(owner, otherBooking.id()).horses().getFirst().deliveryAddressId();
        assertThatThrownBy(() -> jdbc.update("UPDATE booking_horse SET delivery_address_id = ? WHERE booking_id = ?",
                otherAddressId, result.id())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        org.mockito.Mockito.verify(deliveryRoutes).plan(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq(List.of(1, 1)), org.mockito.ArgumentMatchers.eq(2));
    }

    @Test
    void rejectsSecondDeliveryForAnOrderWithoutACompanyDelivery() {
        String owner = customer();
        LocalDate date = pickupDate();
        var departure = data.departure("HCM-DN", date.plusDays(1), 4);
        var primaryHorse = horseInput(horse(owner), InsuranceCoverageOption.NONE, true, List.of());
        var other = horseInput(horse(owner), InsuranceCoverageOption.NONE, true, List.of());
        var secondHorse = new HorseInput(other.horseId(), other.stallTypeCode(), null, null, false, List.of(), other.insurance(), 1);
        var original = request(departure.getId(), date, List.of(primaryHorse, secondHorse));
        var primary = original.addresses().stream().filter(address -> address.addressType() == AddressRole.DELIVERY)
                .findFirst().orElseThrow();
        var addresses = new java.util.ArrayList<>(original.addresses());
        addresses.add(new AddressInput(AddressRole.DELIVERY, "Điểm tự nhận thứ hai", primary.provinceCode(), primary.countryCode(),
                primary.latitude().add(new BigDecimal("0.01")), primary.longitude(), "Người nhận", "0912345678", null, null));
        var request = new BookingCreateRequestDto(original.bookingType(), original.departureId(), date,
                original.emergencySpendingLimit(), original.parties(), addresses, original.horses(),
                true, TransferMethod.COMPANY, TransferMethod.SELF);

        assertThatThrownBy(() -> bookings.submit(owner, "192.0.2.5", request))
                .isInstanceOf(BookingRuleException.class).hasMessageContaining("đặt đơn riêng");
        assertThat(queries.list(owner)).isEmpty();
    }

    @Test
    void privateBookingReservesOneVehicleAndCancellationReleasesIt() {
        String owner = customer();
        LocalDate date = pickupDate().plusWeeks(5);
        var departure = data.departure("HCM-DN", date.plusDays(1), 4);
        var result = bookings.submit(owner, "192.0.2.5", privateRequest(owner, departure.getId(), date, 1));

        assertThat(result.transportMode()).isEqualTo(TransportMode.PRIVATE);
        assertThat(reserved(departure.getId())).isZero();
        assertThat(count("""
                SELECT count(*) FROM departure_vehicle WHERE booking_id = ? AND departure_id = ?
                    AND allotment_type = 'PRIVATE' AND vehicle_count = 1
                """, result.id(), departure.getId())).isEqualTo(1);
        assertThat(queries.get(owner, result.id()).booking().transportMode()).isEqualTo(TransportMode.PRIVATE);
        cancellations.cancelByCustomer(result.id(), owner,
                new com.example.racehorse_transport.dto.booking.CancelBookingRequestDto("Đổi kế hoạch"));
        assertThat(count("SELECT count(*) FROM departure_vehicle WHERE booking_id = ?", result.id())).isZero();
        assertThat(reserved(departure.getId())).isZero();
    }

    @Test
    void rollsBackPrivateBookingWhenNoSingleVehicleCanFit() {
        String owner = customer();
        LocalDate date = pickupDate().plusWeeks(6);
        var departure = data.departure("HCM-DN", date.plusDays(1), 9);
        var request = privateRequest(owner, departure.getId(), date, 10);

        assertThatThrownBy(() -> bookings.submit(owner, "192.0.2.5", request))
                .isInstanceOf(BookingRuleException.class).hasMessageContaining("Chuyến này không còn xe riêng");
        assertThat(queries.list(owner)).isEmpty();
        assertThat(reserved(departure.getId())).isZero();
    }

    private BookingCreateRequestDto privateRequest(String owner, Long departure, LocalDate date, int horses) {
        var shared = request(departure, date, java.util.stream.IntStream.range(0, horses)
                .mapToObj(index -> horseInput(horse(owner), InsuranceCoverageOption.NONE, true, List.of())).toList());
        return new BookingCreateRequestDto(shared.bookingType(), shared.departureId(), shared.requestedPickupDate(),
                shared.emergencySpendingLimit(), shared.parties(), shared.addresses(), shared.horses(),
                shared.acceptedServiceTerms(), shared.pickupMethod(), shared.deliveryMethod(), TransportMode.PRIVATE);
    }

    @Test
    void persistsAllRowsAndReservesStalls() {
        String owner = customer();
        LocalDate date = pickupDate();
        var departure = data.departure("HCM-DN", date.plusDays(1), 4);
        Long addOn = jdbc.queryForObject("""
                INSERT INTO billable_service (code, name, layer, billing_unit, status)
                VALUES (?, 'Test add-on', 'ADD_ON', 'PER_BOOKING', 'ACTIVE') RETURNING id
                """, Long.class, data.unique("A52"));
        var original = request(departure.getId(), date, List.of(
                horseInput(horse(owner), InsuranceCoverageOption.NONE, true, List.of(new AddOnInput(addOn, 2))),
                horseInput(horse(owner), InsuranceCoverageOption.NONE, true, List.of())));
        var request = new BookingCreateRequestDto(original.bookingType(), original.departureId(),
                original.requestedPickupDate(), original.emergencySpendingLimit(), original.parties(),
                original.addresses(), original.horses(), original.acceptedServiceTerms(),
                TransferMethod.SELF, TransferMethod.COMPANY);
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
        // Khách được báo đã nhận đơn, quản lý đang hoạt động được báo đơn mới
        assertThat(count("""
                SELECT count(*) FROM notification n JOIN notification_recipient r ON r.notification_id = n.id
                JOIN booking b ON b.id = n.booking_id AND r.user_id = b.customer_id
                WHERE n.booking_id = ? AND n.title = 'Đã nhận đơn' AND n.link_url = '/orders/' || b.id
                """, id)).isEqualTo(1);
        assertThat(count("""
                SELECT count(*) FROM notification n JOIN notification_recipient r ON r.notification_id = n.id
                JOIN app_user u ON u.id = r.user_id AND u.username = 'manager.dev'
                WHERE n.booking_id = ? AND n.title = 'Đơn mới chờ tiếp nhận'
                """, id)).isEqualTo(1);
        assertThat(reserved(departure.getId())).isEqualTo(2);
        var detail = queries.get(owner, id);
        assertThat(detail.horses()).hasSize(2);
        assertThat(detail.pickupMethod()).isEqualTo(TransferMethod.SELF);
        assertThat(detail.deliveryMethod()).isEqualTo(TransferMethod.COMPANY);
    }

    @Test
    void importUsesTheForeignPickupHubAndRequiresPickupBeforeDeparture() {
        String owner = customer();
        LocalDate departureDate = pickupDate().plusWeeks(3);
        var departure = data.departure("PNH-HCM", departureDate, 4);
        var horse = horseInput(horse(owner), InsuranceCoverageOption.NONE, true, List.of());
        var parties = request(departure.getId(), departureDate, List.of(horse)).parties();
        var addresses = List.of(new AddressInput(AddressRole.PICKUP, "Điểm đón Phnom Penh (mẫu)", "KH-12", "KH",
                new BigDecimal("11.556400"), new BigDecimal("104.928200"), "Liên hệ", "+85512000001", null, null),
                address(AddressRole.DELIVERY, "VN-79"));
        var sameDay = new BookingCreateRequestDto(BookingType.IMPORT, departure.getId(), departureDate, null,
                parties, addresses, List.of(horse), true, TransferMethod.SELF, TransferMethod.COMPANY);

        assertThatThrownBy(() -> bookings.submit(owner, "127.0.0.1", sameDay))
                .isInstanceOf(BookingRuleException.class).hasMessageContaining("trước ngày xuất phát ít nhất một ngày");
        assertThat(reserved(departure.getId())).isZero();

        var oneDayBefore = new BookingCreateRequestDto(BookingType.IMPORT, departure.getId(), departureDate.minusDays(1),
                null, parties, addresses, List.of(horse), true, TransferMethod.SELF, TransferMethod.COMPANY);
        BookingResponseDto booking = bookings.submit(owner, "127.0.0.1", oneDayBefore);
        assertThat(booking.departureId()).isEqualTo(departure.getId());
        assertThat(booking.status()).isEqualTo(BookingStatus.PENDING_INTAKE);
        assertThat(reserved(departure.getId())).isEqualTo(1);
    }

    @Test
    void rejectsNineWideHorsesOnDepartureWithNineSeats() {
        String owner = customer();
        LocalDate date = pickupDate();
        var departure = data.departure("HCM-DN", date.plusDays(1), 9);
        List<HorseInput> wideHorses = java.util.stream.IntStream.range(0, 9)
                .mapToObj(index -> new HorseInput(horse(owner), "WIDE", null, null, false, List.of(),
                        new InsuranceDeclarationRequestDto(InsuranceCoverageOption.NONE, null, null, null, null, true)))
                .toList();
        var request = request(departure.getId(), date, wideHorses);

        withoutSpareTrucks(() -> assertThatThrownBy(() -> bookings.submit(owner, "192.0.2.5", request))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("9 chỗ"));
        assertThat(reserved(departure.getId())).isZero();
        assertThat(queries.list(owner)).isEmpty();
    }

    @Test
    void baseTruckFullAttachesASpareTruckAutomatically() {
        // Quy chế Q3: xe nền đầy thì hệ thống tự gắn xe để sẵn còn rảnh của hub rồi nhận đơn
        String owner = customer();
        LocalDate date = pickupDate();
        var departure = data.departure("HCM-DN", date.plusDays(1), 9);
        List<HorseInput> wideHorses = java.util.stream.IntStream.range(0, 9)
                .mapToObj(index -> new HorseInput(horse(owner), "WIDE", null, null, false, List.of(),
                        new InsuranceDeclarationRequestDto(InsuranceCoverageOption.NONE, null, null, null, null, true)))
                .toList();
        bookings.submit(owner, "192.0.2.6", request(departure.getId(), date, wideHorses));
        assertThat(reserved(departure.getId())).isEqualTo(18);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM departure_vehicle WHERE departure_id = ? AND allotment_type = 'SPARE'",
                Long.class, departure.getId())).isEqualTo(1L);
    }

    @Test
    void fullDepartureRollsBackEverything() {
        String owner = customer();
        LocalDate date = pickupDate();
        var departure = data.departure("HCM-DN", date.plusDays(1), 1);
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
                AND i.insurer_name IS NULL AND i.coverage_amount IS NULL AND i.policy_number IS NULL
                """, result.id())).isEqualTo(1);
        assertThatThrownBy(() -> bookings.submit(owner, null, request))
                .isInstanceOfSatisfying(BookingRuleException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(409);
                    assertThat(ex.getReason()).contains(result.code());
                });
    }

    @Test
    void interprovinceDirectSubmissionRejectsInsufficientDocumentLeadTime() {
        String owner = customer();
        var date = LocalDate.ofInstant(clock.instant(), ZoneId.of("Asia/Ho_Chi_Minh"));
        var local = request(null, date, List.of(horseInput(horse(owner), InsuranceCoverageOption.NONE, true, List.of())));
        var direct = new BookingCreateRequestDto(BookingType.INTERPROVINCE, null, date, new BigDecimal("1000"),
                local.parties(), List.of(address(AddressRole.PICKUP, "VN-79"), address(AddressRole.DELIVERY, "VN-75")),
                local.horses(), true);

        assertThatThrownBy(() -> bookings.submit(owner, null, direct))
                .isInstanceOf(BookingRuleException.class).hasMessageContaining("Không kịp làm giấy trước ngày đón");
        var later = new BookingCreateRequestDto(direct.bookingType(), null, pickupDate(),
                direct.emergencySpendingLimit(), direct.parties(), direct.addresses(), direct.horses(), true);
        assertThat(bookings.submit(owner, null, later).departureId()).isNull();
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
        // Danh sách đơn kèm nơi giao để khách thấy ngựa đi tới đâu
        assertThat(queries.list(owner).getFirst().destinationProvinceName()).isNotBlank();
        assertThat(queries.list(owner).getFirst().destinationCountryName()).isNotBlank();
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

    @Test
    void reviewApiEnforcesRolesOwnershipAndDuplicateStateTransitions() throws Exception {
        String owner = customer();
        String other = customer();
        String specialist = data.unique("a6-specialist");
        String coordinator = data.unique("a6-coordinator");
        Long specialistId = data.staff(specialist, "SPECIALIST").getUserId();
        data.staff(coordinator, "COORDINATOR");
        Long id = bookings.submit(owner, null, directArranged(owner)).id();

        mvc.perform(get("/bookings/review-queue").header(HttpHeaders.AUTHORIZATION, token(coordinator, "COORDINATOR")))
                .andExpect(status().isOk());
        String assignment = "{\"specialistId\":" + specialistId + "}";
        mvc.perform(post("/bookings/{id}/intake", id)
                        .header(HttpHeaders.AUTHORIZATION, token(coordinator, "COORDINATOR"))
                        .contentType("application/json").content(assignment))
                .andExpect(status().isForbidden());
        mvc.perform(post("/bookings/{id}/intake", id)
                        .header(HttpHeaders.AUTHORIZATION, token("manager.dev", "MANAGER"))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/bookings/{id}/intake", id)
                        .header(HttpHeaders.AUTHORIZATION, token(specialist, "SPECIALIST"))
                        .contentType("application/json").content(assignment))
                .andExpect(status().isForbidden());
        mvc.perform(post("/bookings/{id}/intake", Long.MAX_VALUE)
                        .header(HttpHeaders.AUTHORIZATION, token("manager.dev", "MANAGER"))
                        .contentType("application/json").content(assignment))
                .andExpect(status().isNotFound());
        mvc.perform(post("/bookings/{id}/intake", id)
                        .header(HttpHeaders.AUTHORIZATION, token("manager.dev", "MANAGER"))
                        .contentType("application/json").content(assignment))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("UNDER_REVIEW"));
        mvc.perform(post("/bookings/{id}/intake", id)
                        .header(HttpHeaders.AUTHORIZATION, token("manager.dev", "MANAGER"))
                        .contentType("application/json").content(assignment))
                .andExpect(status().isConflict());
        assertThat(review.returnToCustomer(id, specialist, "Thiếu hồ sơ").status()).isEqualTo(BookingStatus.NEEDS_RESUBMISSION);
        mvc.perform(post("/bookings/{id}/resubmit", id).header(HttpHeaders.AUTHORIZATION, token(other, "CUSTOMER")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/bookings/{id}/resubmit", id).header(HttpHeaders.AUTHORIZATION, token(owner, "CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("UNDER_REVIEW"));
        mvc.perform(post("/bookings/{id}/start-quoting", id).header(HttpHeaders.AUTHORIZATION, token(specialist, "SPECIALIST")))
                .andExpect(status().isConflict());
    }

    @Test
    void reassignmentProtectsLegacyBookingsAndPreventsLockingTheCurrentSpecialist() throws Exception {
        String owner = customer();
        String first = data.unique("first-assignee");
        String second = data.unique("second-assignee");
        Long firstId = data.staff(first, "SPECIALIST").getUserId();
        var secondSpecialist = data.staff(second, "SPECIALIST");
        Long secondId = secondSpecialist.getUserId();
        Long id = bookings.submit(owner, null, directArranged(owner)).id();
        jdbc.update("UPDATE booking SET status = 'UNDER_REVIEW' WHERE id = ?", id);

        mvc.perform(post("/bookings/{id}/start-quoting", id).header(HttpHeaders.AUTHORIZATION, token(first, "SPECIALIST")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Đơn chưa có chuyên viên phụ trách."));

        for (Long assignee : List.of(firstId, secondId)) {
            mvc.perform(post("/bookings/{id}/reassign-specialist", id)
                            .header(HttpHeaders.AUTHORIZATION, token("manager.dev", "MANAGER"))
                            .contentType("application/json")
                            .content("{\"specialistId\":" + assignee + ",\"reason\":\"Cân đối công việc\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.specialistId").value(assignee));
        }
        assertThat(count("""
                SELECT count(*) FROM booking_specialist_history
                WHERE booking_id = ? AND reason = 'Cân đối công việc'
                """, id)).isEqualTo(2);

        mvc.perform(post("/bookings/{id}/start-quoting", id).header(HttpHeaders.AUTHORIZATION, token(first, "SPECIALIST")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Đơn chưa được giao cho bạn."));
        assertThatThrownBy(() -> accounts.lockStaffAccount(secondId, "admin.dev",
                new com.example.racehorse_transport.dto.account.StaffAccountStatusRequestDto("Tạm dừng")))
                .isInstanceOf(BookingRuleException.class).hasMessageContaining("1 đơn");
        byte[] workloadResponse = mvc.perform(get("/staff-workloads").param("role", "SPECIALIST")
                        .header(HttpHeaders.AUTHORIZATION, token("manager.dev", "MANAGER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new ObjectMapper().readTree(workloadResponse).path("data")).anySatisfy(row -> {
            assertThat(row.path("staffId").longValue()).isEqualTo(secondId);
            assertThat(row.path("assignedCount").longValue()).isEqualTo(1);
        });
        for (var next : List.of(com.example.racehorse_transport.entity.enums.EmploymentStatus.ON_LEAVE,
                com.example.racehorse_transport.entity.enums.EmploymentStatus.TERMINATED)) {
            var request = new com.example.racehorse_transport.dto.account.StaffAccountUpdateRequestDto(
                    secondSpecialist.getStaffCode(), secondSpecialist.getFullName(), secondSpecialist.getPhone(),
                    secondSpecialist.getHireDate(), secondSpecialist.getHomeHubLocation().getId(), null, null, next);
            assertThatThrownBy(() -> accounts.updateStaffAccount(secondId, request))
                    .isInstanceOf(BookingRuleException.class).hasMessageContaining("1 đơn");
        }
    }

    @Test
    void waiverCanBeConfirmedOnceAndAllowsLocalQuotation() {
        String owner = customer();
        long manager = data.staff(data.unique("quoting-notice-manager"), "MANAGER").getUserId();
        String specialist = data.unique("a6-specialist");
        Long specialistId = data.staff(specialist, "SPECIALIST").getUserId();
        Long id = bookings.submit(owner, null, request(null, pickupDate(), List.of(
                horseInput(horse(owner), InsuranceCoverageOption.NONE, true, List.of())))).id();
        review.intake(id, "manager.dev", specialistId);
        Long insuranceId = queries.get(owner, id).horses().getFirst().insurance().id();
        insurance.confirm(id, insuranceId, specialist);
        assertThatThrownBy(() -> insurance.confirm(id, insuranceId, specialist))
                .isInstanceOfSatisfying(BookingRuleException.class, ex -> assertThat(ex.getStatusCode().value()).isEqualTo(409));
        assertThat(review.startQuoting(id, specialist).status()).isEqualTo(BookingStatus.QUOTING);
        assertThat(jdbc.queryForList("""
                select nr.user_id from notification_recipient nr join notification n on n.id = nr.notification_id
                where n.booking_id = ? and n.title = 'Đơn chờ lập báo giá'
                """, Long.class, id)).contains(manager);
    }

    @Test
    void onlyOneBookingCanMoveToTheLastStallAndRejectReleasesOnlyOnce() throws Exception {
        String specialist = data.unique("a6-specialist");
        Long specialistId = data.staff(specialist, "SPECIALIST").getUserId();
        LocalDate date = pickupDate();
        var departure = data.departure("HCM-DN", date.plusDays(1), 2);
        var replacement = data.departure("HCM-DN", date.plusDays(2), 1);
        String firstOwner = customer();
        String secondOwner = customer();
        Long firstId = bookings.submit(firstOwner, null, request(departure.getId(), date, List.of(
                horseInput(horse(firstOwner), InsuranceCoverageOption.NONE, true, List.of())))).id();
        Long secondId = bookings.submit(secondOwner, null, request(departure.getId(), date, List.of(
                horseInput(horse(secondOwner), InsuranceCoverageOption.NONE, true, List.of())))).id();
        review.intake(firstId, "manager.dev", specialistId);
        review.intake(secondId, "manager.dev", specialistId);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            var first = executor.submit(() -> move(firstId, replacement.getId(), ready, start));
            var second = executor.submit(() -> move(secondId, replacement.getId(), ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }

        assertThat(reserved(departure.getId())).isEqualTo(1);
        assertThat(reserved(replacement.getId())).isEqualTo(1);
        Long winner = queries.reviewDetail(firstId, specialist).booking().departureId().equals(replacement.getId()) ? firstId : secondId;
        assertThat(review.reject(winner, specialist, "Từ chối").status()).isEqualTo(BookingStatus.REJECTED);
        assertThatThrownBy(() -> review.reject(winner, specialist, "Từ chối")).isInstanceOf(BookingRuleException.class);
        assertThat(reserved(departure.getId())).isEqualTo(1);
        assertThat(reserved(replacement.getId())).isZero();
    }

    @Test
    void uploadsAndReviewsBookingDocumentExactlyOnce() {
        String owner = customer();
        String other = customer();
        String specialist = data.unique("a6-specialist");
        Long specialistId = data.staff(specialist, "SPECIALIST").getUserId();
        LocalDate date = pickupDate();
        var departure = data.departure("HCM-DN", date.plusDays(1), 2);
        Long id = bookings.submit(owner, null, request(departure.getId(), date, List.of(
                horseInput(horse(owner), InsuranceCoverageOption.NONE, true, List.of())))).id();
        review.intake(id, "manager.dev", specialistId);
        Long typeId = jdbc.queryForObject("""
                INSERT INTO document_type (code, name, owner_type) VALUES (?, 'Booking document', 'BOOKING')
                RETURNING id
                """, Long.class, data.unique("a6-doc"));
        Long requirementId = jdbc.queryForObject("""
                INSERT INTO document_requirement (country_rule_id, document_type_id, submitter_type, is_mandatory)
                SELECT id, ?, 'CUSTOMER', false FROM country_rule
                WHERE country_code = 'VN' AND direction = 'INTERPROVINCE' RETURNING id
                """, Long.class, typeId);
        var file = new org.springframework.mock.web.MockMultipartFile("file", "transport.pdf", "application/pdf", new byte[]{'%', 'P', 'D', 'F', '-', '1', '.', '7'});
        org.mockito.Mockito.when(cloudinary.uploadFile(file, "documents/booking"))
                .thenReturn("https://example.test/doc.pdf");
        var request = new com.example.racehorse_transport.dto.booking.DocumentUploadRequestDto(
                requirementId, null, date.plusDays(1), file);

        assertThatThrownBy(() -> documentReview.attach(id, other, request))
                .isInstanceOf(com.example.racehorse_transport.exception.ResourceNotFoundException.class);
        var uploaded = documentReview.attach(id, owner, request);
        assertThat(uploaded.requirementId()).isEqualTo(requirementId);
        assertThatThrownBy(() -> documentReview.attach(id, owner, request)).isInstanceOf(BookingRuleException.class);
        var approved = documentReview.approve(id, uploaded.id(), specialist,
                new com.example.racehorse_transport.dto.booking.DocumentApprovalRequestDto("Đã đối chiếu"));
        assertThat(approved.status()).isEqualTo(DocumentStatus.APPROVED);
        assertThat(approved.reviewedAt()).isNotNull();
        assertThat(approved.reviewNote()).isEqualTo("Đã đối chiếu");
        assertThatThrownBy(() -> documentReview.reject(id, uploaded.id(), specialist, "Không hợp lệ"))
                .isInstanceOf(BookingRuleException.class);
        assertThat(documentReview.checklist(id, owner).items()).anySatisfy(item -> {
            assertThat(item.requirement().id()).isEqualTo(requirementId);
            assertThat(item.satisfied()).isTrue();
        });
        assertThat(documentCompleteness.isComplete(id)).isFalse();
    }

    @Test
    void confirmedArrangedInsuranceKeepsTheEvidenceWhenTwoActionsCompete() throws Exception {
        String owner = customer();
        String specialist = data.unique("a6-specialist");
        Long specialistId = data.staff(specialist, "SPECIALIST").getUserId();
        Long id = bookings.submit(owner, null, directArranged(owner)).id();
        review.intake(id, "manager.dev", specialistId);
        Long insuranceId = queries.get(owner, id).horses().getFirst().insurance().id();
        var evidence = new com.example.racehorse_transport.dto.booking.InsuranceEvidenceRequestDto("Hãng mua hộ",
                new BigDecimal("8000000"), new BigDecimal("2500"), "VND", clock.instant().plusSeconds(3600));
        insurance.recordEvidence(id, insuranceId, evidence, specialist);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            java.util.concurrent.Callable<Integer> confirm = () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("Concurrent insurance confirmation did not start.");
                }
                try {
                    insurance.confirm(id, insuranceId, specialist);
                    return 200;
                } catch (BookingRuleException ex) {
                    return ex.getStatusCode().value();
                }
            };
            var first = executor.submit(confirm);
            var second = executor.submit(confirm);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThatThrownBy(() -> insurance.recordEvidence(id, insuranceId, evidence, specialist))
                .isInstanceOf(BookingRuleException.class);
        var confirmed = queries.get(owner, id).horses().getFirst().insurance();
        assertThat(confirmed.status()).isEqualTo(HorseInsuranceStatus.CONFIRMED);
        assertThat(confirmed.premiumAmount()).isEqualByComparingTo("2500");
        assertThat(confirmed.coverageAmount()).isEqualByComparingTo("8000000");
    }

    @Test
    void personalRevisionPreservesTheReasonAndReturnsToDraft() {
        String owner = customer();
        String other = customer();
        String specialist = data.unique("a6-specialist");
        Long specialistId = data.staff(specialist, "SPECIALIST").getUserId();
        LocalDate date = pickupDate();
        var declaration = new InsuranceDeclarationRequestDto(InsuranceCoverageOption.PERSONAL,
                "ORIGINAL-01", date, date.plusDays(5), "Hãng bảo hiểm cá nhân", null);
        when(cloudinary.uploadFile(any(), eq("documents/insurance")))
                .thenReturn("https://example.test/" + data.unique("original") + ".pdf",
                        "https://example.test/" + data.unique("revised") + ".pdf");
        byte[] pdf = {'%', 'P', 'D', 'F', '-', '1', '.', '7'};
        Long id = bookings.submit(owner, null, request(null, date, List.of(
                new HorseInput(horse(owner), "STANDARD", null, null, false, List.of(), declaration))),
                List.of(new MockMultipartFile("insuranceFiles", "original.pdf", "application/pdf", pdf))).id();
        review.intake(id, "manager.dev", specialistId);
        Long insuranceId = queries.get(owner, id).horses().getFirst().insurance().id();
        var revision = new com.example.racehorse_transport.dto.booking.PersonalInsuranceRevisionRequestDto(
                "Hãng bảo hiểm cập nhật", "REVISED-02", date, date.plusDays(8));
        var file = new MockMultipartFile("file", "revised.pdf", "application/pdf", pdf);
        insurance.requestRevision(id, insuranceId, specialist, "Bổ sung số hợp đồng");
        assertThatThrownBy(() -> insurance.revise(id, insuranceId, other, revision, file))
                .isInstanceOf(com.example.racehorse_transport.exception.ResourceNotFoundException.class);
        insurance.revise(id, insuranceId, owner, revision, file);
        var result = queries.get(owner, id).horses().getFirst().insurance();
        assertThat(result.status()).isEqualTo(HorseInsuranceStatus.DRAFT);
        assertThat(result.policyNumber()).isEqualTo("REVISED-02");
        assertThat(result.insurerName()).isEqualTo("Hãng bảo hiểm cập nhật");
        assertThat(result.revisionReason()).isEqualTo("Bổ sung số hợp đồng");
        assertThatThrownBy(() -> insurance.revise(id, insuranceId, owner, revision, file))
                .isInstanceOf(BookingRuleException.class);
    }

    private boolean move(Long bookingId, Long departureId, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Concurrent departure change did not start.");
        }
        try {
            review.changeDeparture(bookingId, departureId);
            return true;
        } catch (IllegalStateException fullDeparture) {
            return false;
        }
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
                new InsuranceDeclarationRequestDto(option, "Ignored", null, null, "Ignored insurer", waiver));
    }

    private int count(String sql, Long... ids) {
        return jdbc.queryForObject(sql, Integer.class, (Object[]) ids);
    }

    private int reserved(Long id) {
        return jdbc.queryForObject("SELECT booked_stall_count FROM departure WHERE id = ?", Integer.class, id);
    }

    private String token(String username, String role) {
        return "Bearer " + jwt.generateToken(username, List.of(role));
    }

    // Cho xe để sẵn của hub TP.HCM tạm bảo dưỡng để kiểm ca hết chỗ thật (quy chế đội xe Q3)
    private void withoutSpareTrucks(Runnable check) {
        String hub = "(SELECT id FROM location WHERE code = 'HUB-HCM')";
        java.util.List<Long> spare = jdbc.queryForList("SELECT id FROM vehicle WHERE status = 'ACTIVE' AND service_role = 'SPARE' AND home_hub_location_id = " + hub, Long.class);
        spare.forEach(id -> jdbc.update("UPDATE vehicle SET status = 'MAINTENANCE' WHERE id = ?", id));
        try {
            check.run();
        } finally {
            spare.forEach(id -> jdbc.update("UPDATE vehicle SET status = 'ACTIVE' WHERE id = ?", id));
        }
    }
}
