package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.BookingResponse;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.repository.RegulationRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpecialistServiceImplTest {

    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final StaffRepository staffRepository = mock(StaffRepository.class);
    private final RegulationRepository regulationRepository = mock(RegulationRepository.class);
    private final LocationRepository locationRepository = mock(LocationRepository.class);

    private SpecialistServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SpecialistServiceImpl(
                bookingRepository,
                staffRepository,
                regulationRepository,
                locationRepository,
                new ObjectMapper(), org.mockito.Mockito.mock(com.example.racehorse_transport.service.StaffNotifier.class));

        Staff specialist = new Staff();
        specialist.setRole("SPECIALIST");
        specialist.setStaffCode("SP-01");
        when(staffRepository.findByUser_Username("specialist")).thenReturn(Optional.of(specialist));
    }

    @Test
    void insuranceCannotBeChangedWhileCustomerIsConsideringIssuedQuote() {
        Booking order = booking(1, "awaiting_payment", "approved");
        order.setDetailsJson("""
            {"intake":{"specialist":{"id":"SP-01"}},"horses":[{"horseId":"H1","insurance":{"option":"assistance","provider":"Provider","premium":1250000,"reviewed":true}}]}
            """);
        when(bookingRepository.findById(1)).thenReturn(Optional.of(order));
        when(bookingRepository.findByIdForUpdate(1)).thenReturn(Optional.of(order));
        var request = com.example.racehorse_transport.dto.booking.HorseInsuranceDto.builder().option("assistance").provider("Other").premium(java.math.BigDecimal.ONE).reviewed(true).build();
        assertThatThrownBy(() -> service.updateInsurance("BK-1", "H1", request, "specialist")).isInstanceOf(IllegalStateException.class);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void repeatedQuoteConfirmationDoesNotSaveOrAppendHistory() {
        Booking order = confirmedInsuranceBooking("under_review", false);
        String original = order.getDetailsJson();
        var request = confirmedInsuranceRequest(false);
        // Frontend normalizes JSON null collections; PostgreSQL money may contain a decimal scale.
        request.setDocuments(List.of());
        request.setPolicyDocuments(List.of());
        request.setDocumentNames(Map.of());
        request.setPremium(new java.math.BigDecimal("1250000.00"));
        service.updateInsurance("BK-1", "H1", request, "specialist");
        service.updateInsurance("BK-1", "H1", request, "specialist");
        verify(bookingRepository, never()).save(any());
        assertThat(order.getDetailsJson()).isEqualTo(original);
    }

    @Test
    void unchangedDraftCannotUndoConfirmedQuote() {
        Booking order = confirmedInsuranceBooking("pending_commercial", false);
        String original = order.getDetailsJson();
        var request = confirmedInsuranceRequest(false);
        request.setReviewed(false);
        assertThatThrownBy(() -> service.updateInsurance("BK-1", "H1", request, "specialist"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("đã được xác nhận");
        verify(bookingRepository, never()).save(any());
        assertThat(order.getDetailsJson()).isEqualTo(original);
    }

    @Test
    void changedQuoteCanBeSavedAsDraftBeforeIssueAndNeedsConfirmationAgain() {
        Booking order = confirmedInsuranceBooking("under_review", false);
        var draft = confirmedInsuranceRequest(false);
        draft.setPremium(new java.math.BigDecimal("2250000"));
        draft.setReviewed(false);
        var saved = service.updateInsurance("BK-1", "H1", draft, "specialist");
        assertThat(saved.getHorses().getFirst().getInsurance().getReviewed()).isFalse();
        assertThat(saved.getHorses().getFirst().getInsurance().getPremium()).isEqualByComparingTo("2250000");
        var confirmed = confirmedInsuranceRequest(false);
        confirmed.setPremium(new java.math.BigDecimal("2250000"));
        var reviewed = service.updateInsurance("BK-1", "H1", confirmed, "specialist");
        assertThat(reviewed.getHorses().getFirst().getInsurance().getReviewed()).isTrue();
        assertThat(order.getDetailsJson()).contains("2250000");
    }

    @Test
    void newInsuranceConfirmationRecordsActualActorAndKeepsStampOnRetry() throws Exception {
        Booking order = confirmedInsuranceBooking("under_review", false);
        normalizeInsuranceBooking(order);
        var actor = staffRepository.findByUser_Username("specialist").orElseThrow();
        var user = new com.example.racehorse_transport.entity.User();
        user.setId(8);
        user.setUsername("specialist");
        actor.setUser(user);
        var draft = confirmedInsuranceRequest(false);
        draft.setPremium(new java.math.BigDecimal("2250000"));
        draft.setReviewed(false);
        service.updateInsurance("BK-1", "H1", draft, "specialist");
        var confirmed = confirmedInsuranceRequest(false);
        confirmed.setPremium(new java.math.BigDecimal("2250000"));

        service.updateInsurance("BK-1", "H1", confirmed, "specialist");

        var policy = order.getBookingHorses().getFirst().getInsurancePolicy();
        assertThat(policy.getReviewedBy()).isSameAs(user);
        assertThat(policy.getReviewedAt()).isNotNull();
        var confirmedAt = policy.getReviewedAt();
        service.updateInsurance("BK-1", "H1", confirmed, "specialist");
        assertThat(policy.getReviewedAt()).isEqualTo(confirmedAt);
        assertThat(policy.getArrangedAt()).isNull();
    }

    @Test
    void unchangedLegacyConfirmationDoesNotInventReviewerOrTimestamp() throws Exception {
        Booking order = confirmedInsuranceBooking("under_review", false);
        normalizeInsuranceBooking(order);

        service.updateInsurance("BK-1", "H1", confirmedInsuranceRequest(false), "specialist");

        var policy = order.getBookingHorses().getFirst().getInsurancePolicy();
        assertThat(policy.getReviewedBy()).isNull();
        assertThat(policy.getReviewedAt()).isNull();
        assertThat(policy.getArrangedAt()).isNull();
    }

    @Test
    void arrangingPolicyRecordsActorWithoutInventingLegacyQuoteReviewMetadata() throws Exception {
        Booking order = confirmedInsuranceBooking("clearance_in_progress", false);
        normalizeInsuranceBooking(order);
        var actor = staffRepository.findByUser_Username("specialist").orElseThrow();
        var user = new com.example.racehorse_transport.entity.User();
        user.setId(8);
        actor.setUser(user);

        service.updateInsurance("BK-1", "H1", confirmedInsuranceRequest(true), "specialist");

        var policy = order.getBookingHorses().getFirst().getInsurancePolicy();
        assertThat(policy.getArrangedBy()).isSameAs(user);
        assertThat(policy.getArrangedAt()).isNotNull();
        assertThat(policy.getReviewedBy()).isNull();
        assertThat(policy.getReviewedAt()).isNull();
        var arrangedAt = policy.getArrangedAt();
        service.updateInsurance("BK-1", "H1", confirmedInsuranceRequest(true), "specialist");
        assertThat(policy.getArrangedAt()).isEqualTo(arrangedAt);
    }

    @Test
    void invalidDraftPolicyDateFailsBeforeSavingOrChangingNormalizedInsurance() throws Exception {
        Booking order = confirmedInsuranceBooking("under_review", false);
        var json = new ObjectMapper().readTree(order.getDetailsJson());
        var insurance = (com.fasterxml.jackson.databind.node.ObjectNode) json.path("horses").get(0).path("insurance");
        insurance.put("option", "personal").put("reviewed", false).put("policyNumber", "PERSONAL-1")
                .put("validFrom", "2026-01-01").put("validUntil", "2027-12-31");
        order.setDetailsJson(json.toString());
        normalizeInsuranceBooking(order);
        String original = order.getDetailsJson();
        var request = com.example.racehorse_transport.dto.booking.HorseInsuranceDto.builder()
                .option("personal").provider("Provider").policyNumber("PERSONAL-1")
                .validFrom("invalid-date").validUntil("2027-12-31").reviewed(false).arranged(false).build();

        assertThatThrownBy(() -> service.updateInsurance("BK-1", "H1", request, "specialist"))
                .isInstanceOf(IllegalStateException.class)
                .hasRootCauseInstanceOf(java.time.format.DateTimeParseException.class);

        verify(bookingRepository, never()).save(any());
        assertThat(order.getDetailsJson()).isEqualTo(original);
        assertThat(order.getBookingHorses().getFirst().getInsurancePolicy().getValidFrom())
                .isEqualTo(java.time.LocalDate.of(2026, 1, 1));
    }

    private void normalizeInsuranceBooking(Booking order) throws Exception {
        var json = new ObjectMapper().readTree(order.getDetailsJson());
        var horse = (com.fasterxml.jackson.databind.node.ObjectNode) json.path("horses").get(0);
        horse.set("selectedServices", new ObjectMapper().readTree("""
                {"stall":{"id":1,"serviceCode":"STALL_STANDARD","price":0},
                 "feed":{"id":2,"serviceCode":"FEED_BASIC","price":0},
                 "water":{"id":3,"serviceCode":"WATER_EVERY_3H","price":0}}
                """));
        order.setDetailsJson(json.toString());
    }

    @Test
    void repeatedActivePolicyConfirmationDoesNotSaveAgain() {
        Booking order = confirmedInsuranceBooking("clearance_in_progress", true);
        String original = order.getDetailsJson();
        var request = confirmedInsuranceRequest(true);
        service.updateInsurance("BK-1", "H1", request, "specialist");
        verify(bookingRepository, never()).save(any());
        assertThat(order.getDetailsJson()).isEqualTo(original);
    }

    @Test
    void activePolicyCannotBeChangedOrRevertedToDraft() {
        Booking order = confirmedInsuranceBooking("clearance_in_progress", true);
        String original = order.getDetailsJson();
        var request = confirmedInsuranceRequest(true);
        request.setArranged(false);
        assertThatThrownBy(() -> service.updateInsurance("BK-1", "H1", request, "specialist"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("có hiệu lực");
        request.setArranged(true);
        request.setPolicyNumber("CHANGED");
        assertThatThrownBy(() -> service.updateInsurance("BK-1", "H1", request, "specialist"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("có hiệu lực");
        verify(bookingRepository, never()).save(any());
        assertThat(order.getDetailsJson()).isEqualTo(original);
    }

    private Booking confirmedInsuranceBooking(String status, boolean policyActive) {
        Booking order = booking(1, status, "approved");
        order.setDetailsJson("""
            {"intake":{"specialist":{"id":"SP-01"}},"horses":[{"horseId":"H1","name":"Storm",
             "insurance":{"option":"assistance","provider":"Provider","premium":1250000,"reviewed":true,
             "arranged":%s,"policyNumber":%s,"validFrom":%s,"validUntil":%s}}],
             "history":[{"time":1,"actor":"specialist","text":"Confirmed"}]}
            """.formatted(policyActive, policyActive ? "\"POLICY-1\"" : "null",
                    policyActive ? "\"2026-01-01\"" : "null", policyActive ? "\"2027-12-31\"" : "null"));
        when(bookingRepository.findByIdForUpdate(1)).thenReturn(Optional.of(order));
        return order;
    }

    private com.example.racehorse_transport.dto.booking.HorseInsuranceDto confirmedInsuranceRequest(boolean policyActive) {
        return com.example.racehorse_transport.dto.booking.HorseInsuranceDto.builder()
                .option("assistance").provider("Provider").premium(new java.math.BigDecimal("1250000"))
                .reviewed(true).arranged(policyActive).policyNumber(policyActive ? "POLICY-1" : null)
                .validFrom(policyActive ? "2026-01-01" : null).validUntil(policyActive ? "2027-12-31" : null).build();
    }

    @Test
    void afterDepositCanAttachEffectivePolicyWithoutChangingQuotedPremiumOrProvider() {
        Booking order = booking(1, "clearance_in_progress", "approved");
        order.setDetailsJson("""
            {"intake":{"specialist":{"id":"SP-01"}},"horses":[{"horseId":"H1","insurance":{"option":"assistance","provider":"Provider","premium":1250000,"reviewed":true}}],"quote":{"insuranceSnapshot":[{"horseId":"H1","insurance":{"premium":1250000}}]}}
            """);
        when(bookingRepository.findById(1)).thenReturn(Optional.of(order));
        when(bookingRepository.findByIdForUpdate(1)).thenReturn(Optional.of(order));
        var request = com.example.racehorse_transport.dto.booking.HorseInsuranceDto.builder().option("none").provider("Other").premium(java.math.BigDecimal.ONE)
            .policyNumber("POLICY-1").validFrom("2026-01-01").validUntil("2027-12-31").arranged(true).build();
        var response = service.updateInsurance("BK-1", "H1", request, "specialist");
        var insurance = response.getHorses().getFirst().getInsurance();
        assertThat(insurance.getPremium()).isEqualByComparingTo("1250000");
        assertThat(insurance.getProvider()).isEqualTo("Provider");
        assertThat(insurance.getOption()).isEqualTo("assistance");
        assertThat(insurance.getArranged()).isTrue();
        assertThat(((Map<?, ?>) response.getQuote()).get("insuranceSnapshot")).isNotNull();
    }

    @Test
    void getVerificationBookings_keepsApprovedBookingsUntilQuotationIsIssued() {
        when(bookingRepository.findAllByOrderByBookingDateDesc()).thenReturn(List.of(
                booking(1, "pending_commercial", "approved"),
                booking(2, "awaiting_payment", "approved"),
                booking(3, "under_review", "pending")));

        List<BookingResponse> bookings = service.getVerificationBookings("specialist");

        assertThat(bookings).extracting(BookingResponse::getId)
                .containsExactly("BK-001", "BK-003");
    }

    @Test
    void getBookingDetailHidesLegacyInternationalDocumentsForDomesticBooking() {
        Booking domestic = booking(4, "clearance_in_progress", "approved");
        domestic.setBookingType("domestic");
        domestic.setDetailsJson("""
                {
                  "intake": { "specialist": { "id": "SP-01" } },
                  "clearance": {
                    "items": [
                      { "type": "health_cert" },
                      { "type": "import_permit" },
                      { "type": "customs_declaration" }
                    ]
                  }
                }
                """);
        when(bookingRepository.findById(4)).thenReturn(Optional.of(domestic));

        BookingResponse response = service.getBookingDetail("BK-004", "specialist");

        Map<?, ?> clearance = (Map<?, ?>) response.getClearance();
        List<?> items = (List<?>) clearance.get("items");
        assertThat(items).hasSize(1);
        assertThat(((Map<?, ?>) items.get(0)).get("type")).isEqualTo("health_cert");
    }

    @Test
    void acceptWaybillUsesDomesticFallbackDocumentsWhenNoRegulationsExist() {
        Booking domestic = booking(5, "waybill_issued", "approved");
        domestic.setBookingType("domestic");
        domestic.setDetailsJson("""
                {
                  "intake": { "specialist": { "id": "SP-01" } }
                }
                """);
        when(bookingRepository.findById(5)).thenReturn(Optional.of(domestic));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = service.acceptWaybill("BK-005", "specialist");

        Map<?, ?> clearance = (Map<?, ?>) response.getClearance();
        List<?> items = (List<?>) clearance.get("items");
        assertThat(items).hasSize(2);
        assertThat(((Map<?, ?>) items.get(0)).get("type")).isEqualTo("health_cert");
        assertThat(((Map<?, ?>) items.get(1)).get("type")).isEqualTo("poa");
    }

    @Test
    void updateClearanceItemRejectsInternationalDocumentTypesOnDomesticBooking() {
        Booking domestic = booking(6, "clearance_in_progress", "approved");
        domestic.setBookingType("domestic");
        domestic.setDetailsJson("""
                {
                  "intake": { "specialist": { "id": "SP-01" } },
                  "clearance": { "items": [{ "type": "health_cert" }] }
                }
                """);
        when(bookingRepository.findById(6)).thenReturn(Optional.of(domestic));

        assertThatThrownBy(() -> service.updateClearanceItem(
                "BK-006",
                com.example.racehorse_transport.dto.specialist.UpdateClearanceItemRequest.builder()
                        .type("import_permit")
                        .build(),
                "specialist"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không áp dụng cho đơn trong nước");

        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void beforeQuoteStoresDraftWithoutContractOrReviewAndAllowsPartialPersonalDraft() {
        Booking order = booking(1, "under_review", "pending");
        order.setDetailsJson("{\"intake\":{\"specialist\":{\"id\":\"SP-01\"}},\"horses\":[{\"horseId\":\"H1\",\"insurance\":{\"option\":\"assistance\"}}]}");
        when(bookingRepository.findByIdForUpdate(1)).thenReturn(Optional.of(order));
        var request = com.example.racehorse_transport.dto.booking.HorseInsuranceDto.builder().option("assistance")
            .policyNumber("SHOULD-NOT-BE-SAVED").policyDocuments(List.of("https://example.test/policy.pdf")).reviewed(false).build();
        var result = service.updateInsurance("BK-1", "H1", request, "specialist").getHorses().getFirst().getInsurance();
        assertThat(result.getPolicyNumber()).isNull();
        assertThat(result.getPolicyDocuments()).isNull();
        assertThat(result.getReviewed()).isFalse();
        order.setDetailsJson(order.getDetailsJson().replace("assistance", "personal"));
        request.setOption("personal");
        assertThat(service.updateInsurance("BK-1", "H1", request, "specialist").getHorses().getFirst().getInsurance().getReviewed()).isFalse();
    }

    @Test
    void afterDepositPreservesQuoteFilesAndAcceptsSeparatePolicyDocuments() {
        Booking order = booking(1, "clearance_in_progress", "approved");
        order.setDetailsJson("""
          {"intake":{"specialist":{"id":"SP-01"}},"horses":[{"horseId":"H1","insurance":{"option":"assistance","provider":"Provider","premium":1250000,"reviewed":true,"documents":["https://example.test/quote.pdf"],"note":"Quoted terms","quoteValidUntil":"2026-12-31"}}]}
          """);
        when(bookingRepository.findByIdForUpdate(1)).thenReturn(Optional.of(order));
        var request = com.example.racehorse_transport.dto.booking.HorseInsuranceDto.builder().option("assistance")
            .documents(List.of("https://example.test/tampered.pdf")).note("Edited terms").quoteValidUntil("2027-01-01")
            .policyDocuments(List.of("https://example.test/policy.pdf")).build();
        var result = service.updateInsurance("BK-1", "H1", request, "specialist").getHorses().getFirst().getInsurance();
        assertThat(result.getDocuments()).containsExactly("https://example.test/quote.pdf");
        assertThat(result.getPolicyDocuments()).containsExactly("https://example.test/policy.pdf");
        assertThat(result.getQuoteValidUntil()).isEqualTo("2026-12-31");
        assertThat(result.getNote()).isEqualTo("Quoted terms");
    }

    @Test
    void insuranceListContainsAllAssignedPhasesButExcludesOtherSpecialists() {
        Booking assigned = booking(1, "completed", "approved");
        Booking other = booking(2, "under_review", "pending");
        other.setDetailsJson(other.getDetailsJson().replace("SP-01", "SP-99"));
        when(bookingRepository.findAllByOrderByBookingDateDesc()).thenReturn(List.of(assigned, other));
        assertThat(service.getInsuranceBookings("specialist")).extracting(r -> r.getNumericId()).containsExactly(1);
    }

    private Booking booking(int id, String status, String medicalStatus) {
        return Booking.builder()
                .id(id)
                .status(status)
                .detailsJson("""
                        {
                          "intake": { "specialist": { "id": "SP-01" } },
                          "medical": { "status": "%s" }
                        }
                        """.formatted(medicalStatus))
                .build();
    }

}
