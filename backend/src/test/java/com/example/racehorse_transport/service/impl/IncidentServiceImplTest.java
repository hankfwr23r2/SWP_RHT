package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.incident.IncidentResponse;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.repository.BookingAssignmentRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.IncidentRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.SystemLogRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IncidentServiceImplTest {

    private final IncidentRepository incidentRepository = mock(IncidentRepository.class);

    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final StaffRepository staffRepository = mock(StaffRepository.class);
    private final com.example.racehorse_transport.service.HorseHealthLogService healthService = mock(com.example.racehorse_transport.service.HorseHealthLogService.class);

    private final IncidentServiceImpl service = new IncidentServiceImpl(
            mock(com.example.racehorse_transport.service.CaptureService.class),
            incidentRepository,
            bookingRepository,
            mock(BookingAssignmentRepository.class),
            staffRepository,
            mock(SystemLogRepository.class),
            healthService, org.mockito.Mockito.mock(com.example.racehorse_transport.service.StaffNotifier.class));

    @Test
    void customerIncidentsAreLoadedForTheSignedInCustomerInOneQueryWithoutInternalFields() {
        Booking booking = new Booking();
        booking.setId(3);
        Incident incident = Incident.builder()
                .id(7)
                .bookingID(booking)
                .incidentType("VEHICLE_BREAKDOWN")
                .status("active")
                .description("internal note")
                .partnerName("Partner Co")
                .estimatedCost(new BigDecimal("1000000"))
                .build();
        when(incidentRepository.findByBookingID_CustomerID_User_Username("customer@example.com"))
                .thenReturn(List.of(incident));

        List<IncidentResponse> incidents = service.getCustomerIncidents("customer@example.com");

        assertThat(incidents).hasSize(1);
        IncidentResponse response = incidents.get(0);
        assertThat(response.getBookingId()).isEqualTo(3);
        assertThat(response.getBookingCode()).isEqualTo("BK-003");
        assertThat(response.getStatus()).isEqualTo("active");
        assertThat(response.getDescription()).isNull();
        assertThat(response.getPartnerName()).isNull();
        assertThat(response.getEstimatedCost()).isNull();
    }

    @Test
    void incidentsOfSeveralBookingsAreLoadedInOneQuery() {
        Booking first = new Booking();
        first.setId(1);
        Booking second = new Booking();
        second.setId(2);
        when(incidentRepository.findByBookingID_IdIn(List.of(1, 2))).thenReturn(List.of(
                Incident.builder().id(7).bookingID(first).status("active").description("Nổ lốp").build(),
                Incident.builder().id(8).bookingID(second).status("resolved").build()));

        List<IncidentResponse> incidents = service.getIncidentsForBookings(List.of(1, 2));

        assertThat(incidents).extracting(IncidentResponse::getBookingId).containsExactly(1, 2);
        assertThat(incidents.get(0).getDescription()).isEqualTo("Nổ lốp");
    }

    @Test
    void approvingRerouteUpdatesOnlyAffectedTripAndPreservesCommercialPlan() throws Exception {
        Booking booking = new Booking();
        booking.setId(3);
        booking.setStatus("pending_emergency_approval");
        booking.setDetailsJson("""
                {"quote":{"total":1000000},"route":{"legs":[{"arriveAt":100}]},
                 "trips":[{"tripId":"T1","vehicleId":"V1","driverId":"D1","escortId":"E1",
                   "run":{"checkpoints":[{"type":"delivery","plannedAt":100}]}},
                   {"tripId":"T2","vehicleId":"V2","driverId":"D2"}]}
                """);
        Incident incident = Incident.builder().id(7).bookingID(booking).tripId("T1")
                .incidentType("TRAFFIC_JAM").resolutionType("REROUTE").status("proposed")
                .planJson("{\"action\":\"reroute\",\"newEta\":200,\"detour\":{\"path\":[[11,104],[12,105]]}}")
                .build();
        when(incidentRepository.findById(7)).thenReturn(java.util.Optional.of(incident));
        when(incidentRepository.save(incident)).thenReturn(incident);
        when(incidentRepository.findByBookingID_Id(3)).thenReturn(List.of(incident));
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode before = mapper.valueToTree(mapper.readValue(booking.getDetailsJson(),
                com.example.racehorse_transport.dto.booking.BookingDetailsPayload.class));

        service.approveIncidentPlan(7, new com.example.racehorse_transport.dto.incident.ApproveIncidentPlanRequest(), "manager");

        var after = mapper.readTree(booking.getDetailsJson());
        assertThat(after.path("quote")).isEqualTo(before.path("quote"));
        assertThat(after.path("route")).isEqualTo(before.path("route"));
        assertThat(after.path("trips").get(1)).isEqualTo(before.path("trips").get(1));
        var trip = after.path("trips").get(0);
        assertThat(trip.path("vehicleId").asText()).isEqualTo("V1");
        assertThat(trip.path("driverId").asText()).isEqualTo("D1");
        assertThat(trip.path("escortId").asText()).isEqualTo("E1");
        assertThat(trip.path("incidentRoute").path("newEta").asLong()).isEqualTo(200);
        assertThat(trip.path("run").path("checkpoints").get(0).path("plannedAt").asLong()).isEqualTo(200);
    }

    @Test
    void cannotApproveRerouteWithoutRoadGeometry() {
        Incident incident = Incident.builder().id(7).resolutionType("REROUTE").status("proposed").build();
        when(incidentRepository.findById(7)).thenReturn(java.util.Optional.of(incident));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.approveIncidentPlan(7,
                new com.example.racehorse_transport.dto.incident.ApproveIncidentPlanRequest(), "manager"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("đường đi");
        assertThat(incident.getStatus()).isEqualTo("proposed");
    }

    private Incident healthIncident(boolean arrived) {
        Booking booking = new Booking(); booking.setId(3); booking.setStatus("emergency_plan_active");
        long arrival = System.currentTimeMillis() - 1000;
        booking.setDetailsJson("{\"quote\":{\"total\":1000000},\"trips\":[{\"tripId\":\"T1\",\"horseIds\":[\"H-1\"],"
                + "\"run\":{\"startedAt\":1,\"checkpoints\":[{\"id\":\"pickup\",\"doneAt\":2},{\"id\":\"sos-7\",\"type\":\"rest\",\"incidentId\":7,\"restMinutes\":30"
                + (arrived ? ",\"arrivedAt\":" + arrival : "") + "},{\"id\":\"delivery\",\"type\":\"delivery\",\"plannedAt\":100}]}}]}");
        Incident incident = Incident.builder().id(7).bookingID(booking).tripId("T1").status("active")
                .incidentType("HORSE_HEALTH").resolutionType("EXTERNAL_PARTNER")
                .planJson("{\"action\":\"to_station\",\"horseIds\":[\"H-1\"],\"station\":\"Trạm A\",\"restMinutes\":30,\"newEta\":200,"
                        + "\"toStation\":{\"path\":[[11,104],[12,105]],\"hours\":1},\"onward\":{\"path\":[[12,105],[13,106]],\"hours\":2}}")
                .build();
        when(incidentRepository.findById(7)).thenReturn(java.util.Optional.of(incident));
        when(incidentRepository.save(incident)).thenReturn(incident);
        when(incidentRepository.findByBookingID_Id(3)).thenReturn(List.of(incident));
        return incident;
    }

    @Test
    void healthApprovalAddsStationBeforePendingCheckpointAndKeepsCompletedHistory() throws Exception {
        Incident incident = healthIncident(false); incident.setStatus("proposed");
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var payload = mapper.readValue(incident.getBookingID().getDetailsJson(), com.example.racehorse_transport.dto.booking.BookingDetailsPayload.class);
        @SuppressWarnings("unchecked")
        var checkpoints = (List<java.util.Map<String, Object>>) payload.getTrips().get(0).getRun().get("checkpoints");
        checkpoints.remove(1);
        incident.getBookingID().setDetailsJson(mapper.writeValueAsString(payload));
        service.approveIncidentPlan(7, new com.example.racehorse_transport.dto.incident.ApproveIncidentPlanRequest(), "manager");
        var after = mapper.readTree(incident.getBookingID().getDetailsJson());
        var trip = after.path("trips").get(0);
        assertThat(trip.path("run").path("checkpoints").get(0).path("doneAt").asLong()).isEqualTo(2);
        assertThat(trip.path("run").path("checkpoints").get(1).path("id").asText()).isEqualTo("sos-7");
        assertThat(trip.path("run").path("checkpoints").get(2).path("plannedAt").asLong()).isEqualTo(200);
        assertThat(trip.path("incidentRoute").path("onward").path("path").size()).isEqualTo(2);
        assertThat(after.path("quote").path("total").asLong()).isEqualTo(1000000);
    }

    @Test
    void cannotConfirmHorseFitBeforeCheckingInAtSosStation() {
        healthIncident(false);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.confirmFit(7, null, "escort", "ADMIN"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tới trạm");
        verifyNoInteractions(healthService);
    }

    // Bản demo không bắt kíp xe chờ: rời trạm SOS trước giờ nghỉ đã duyệt được, nhưng phải xác nhận rõ ràng
    @Test
    void leavingSosStationEarlyNeedsAnExplicitConfirmation() {
        Incident incident = healthIncident(true); incident.setFitConfirmedAt(java.time.Instant.now());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.resumeJourney(7, "driver", "ADMIN"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("xác nhận rời trạm sớm");
        assertThat(incident.getStatus()).isEqualTo("active");
    }

    @Test
    void confirmedEarlyLeaveIsAllowedAndRecorded() throws Exception {
        Incident incident = healthIncident(true); incident.setFitConfirmedAt(java.time.Instant.now());
        service.resumeJourney(7, "driver", "ADMIN", true, "Ngựa đã ổn, đi tiếp cho kịp giờ");
        assertThat(incident.getStatus()).isEqualTo("resolved");
        var sos = new com.fasterxml.jackson.databind.ObjectMapper().readTree(incident.getBookingID().getDetailsJson())
                .path("trips").get(0).path("run").path("checkpoints").get(1);
        assertThat(sos.path("doneAt").asLong()).isPositive();
        assertThat(sos.path("earlyLeave").path("note").asText()).isEqualTo("Ngựa đã ổn, đi tiếp cho kịp giờ");
        assertThat(sos.path("earlyLeave").path("restedMinutes").asLong()).isLessThan(sos.path("restMinutes").asLong());
        assertThat(sos.path("earlyLeave").path("by").asText()).isEqualTo("driver");
    }

    // Hộ tống xác nhận đủ sức trước khi hết giờ nghỉ đã duyệt: không chặn cứng, nhưng phải xác nhận rõ ràng
    @Test
    void confirmingFitBeforeTheRestEndsNeedsAnExplicitConfirmation() {
        Incident incident = healthIncident(true);
        var request = new com.example.racehorse_transport.dto.incident.ConfirmIncidentFitRequest();
        request.setPhoto("care.jpg");
        var care = new com.example.racehorse_transport.dto.incident.ConfirmIncidentFitRequest.CareRecord();
        care.setHorseId("H-1"); care.setCareNote("Đã ổn định");
        request.setHorses(List.of(care));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.confirmFit(7, request, "escort", "ADMIN"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Cần xác nhận");
        assertThat(incident.getFitConfirmedAt()).isNull();
        verifyNoInteractions(healthService);
    }

    @Test
    void recordingCareWritesHorseHealthLogAndJourneyWelfareTogether() throws Exception {
        Incident incident = healthIncident(true);
        var escort = new com.example.racehorse_transport.entity.Staff(); escort.setId(9);
        when(staffRepository.findByUser_Username("escort")).thenReturn(java.util.Optional.of(escort));
        var request = new com.example.racehorse_transport.dto.incident.ConfirmIncidentFitRequest();
        request.setPhoto("care.jpg");
        var care = new com.example.racehorse_transport.dto.incident.ConfirmIncidentFitRequest.CareRecord();
        care.setHorseId("H-1"); care.setCareNote("Đã chăm sóc, ổn định và đủ sức đi tiếp");
        request.setHorses(List.of(care));
        request.setConfirmEarly(true); // ngựa vừa tới trạm, chưa nghỉ đủ 30 phút
        service.confirmFit(7, request, "escort", "ADMIN");
        org.mockito.Mockito.verify(healthService).updateHorseHealthStatusByEscort(1, 3,
                "ĐỦ SỨC ĐI TIẾP SAU SOS", null, null, "INC-7: " + care.getCareNote(), 9);
        assertThat(incident.getFitConfirmedAt()).isNotNull();
        var payload = new com.fasterxml.jackson.databind.ObjectMapper().readTree(incident.getBookingID().getDetailsJson());
        assertThat(payload.path("trips").get(0).path("run").path("welfare").get(0).path("checkpointId").asText()).isEqualTo("sos-7");
    }

    @Test
    void leavingStationCompletesSosCheckpointAndKeepsDeliveryPending() throws Exception {
        Incident incident = healthIncident(true); incident.setFitConfirmedAt(java.time.Instant.now());
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var payload = mapper.readValue(incident.getBookingID().getDetailsJson(), com.example.racehorse_transport.dto.booking.BookingDetailsPayload.class);
        @SuppressWarnings("unchecked")
        var checkpoints = (List<java.util.Map<String, Object>>) payload.getTrips().get(0).getRun().get("checkpoints");
        checkpoints.get(1).put("arrivedAt", System.currentTimeMillis() - 31 * 60000L);
        incident.getBookingID().setDetailsJson(mapper.writeValueAsString(payload));
        service.resumeJourney(7, "driver", "ADMIN");
        var after = mapper.readTree(incident.getBookingID().getDetailsJson()).path("trips").get(0).path("run").path("checkpoints");
        assertThat(after.get(1).path("doneAt").asLong()).isPositive();
        assertThat(after.get(2).hasNonNull("doneAt")).isFalse();
        assertThat(incident.getStatus()).isEqualTo("resolved");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void stationCanReplaceOnlyPendingNextRestAndArchivesTheOriginal(boolean explicitReplacement) throws Exception {
        Incident incident = healthIncident(false); incident.setStatus("proposed");
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var payload = mapper.readValue(incident.getBookingID().getDetailsJson(), com.example.racehorse_transport.dto.booking.BookingDetailsPayload.class);
        @SuppressWarnings("unchecked")
        var checkpoints = (List<java.util.Map<String, Object>>) payload.getTrips().get(0).getRun().get("checkpoints");
        checkpoints.get(1).put("id", "rest-old"); checkpoints.get(1).remove("incidentId");
        var plan = mapper.readValue(incident.getPlanJson(), new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
        if (explicitReplacement) plan.put("replaceRestCheckpointId", "rest-old");
        else checkpoints.get(1).put("place", "Trạm A");
        incident.setPlanJson(mapper.writeValueAsString(plan));
        incident.getBookingID().setDetailsJson(mapper.writeValueAsString(payload));
        service.approveIncidentPlan(7, new com.example.racehorse_transport.dto.incident.ApproveIncidentPlanRequest(), "manager");
        var run = mapper.readTree(incident.getBookingID().getDetailsJson()).path("trips").get(0).path("run");
        assertThat(run.path("checkpoints").size()).isEqualTo(3);
        assertThat(run.path("checkpoints").get(1).path("id").asText()).isEqualTo("sos-7");
        assertThat(run.path("supersededCheckpoints").get(0).path("id").asText()).isEqualTo("rest-old");
    }

    @Test
    void stationReplacementCannotSkipBorderCheckpoint() throws Exception {
        Incident incident = healthIncident(false); incident.setStatus("proposed");
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var payload = mapper.readValue(incident.getBookingID().getDetailsJson(), com.example.racehorse_transport.dto.booking.BookingDetailsPayload.class);
        @SuppressWarnings("unchecked")
        var checkpoints = (List<java.util.Map<String, Object>>) payload.getTrips().get(0).getRun().get("checkpoints");
        checkpoints.get(1).put("type", "border"); checkpoints.get(1).put("id", "border");
        var plan = mapper.readValue(incident.getPlanJson(), new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
        plan.put("replaceRestCheckpointId", "border"); incident.setPlanJson(mapper.writeValueAsString(plan));
        incident.getBookingID().setDetailsJson(mapper.writeValueAsString(payload));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.approveIncidentPlan(7,
                new com.example.racehorse_transport.dto.incident.ApproveIncidentPlanRequest(), "manager"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cửa khẩu");
        assertThat(incident.getStatus()).isEqualTo("proposed");
    }

    @Test
    void horseHealthExpensesRespectManagerApprovedPayer() {
        Incident incident = healthIncident(true); incident.setCostBearer("COMPANY");
        var expense = new com.example.racehorse_transport.dto.incident.IncidentExpenseRequest();
        expense.setCategory("vet_fee"); expense.setLabel("Khám tại trạm"); expense.setPhoto("invoice.jpg");
        expense.setAmount(new BigDecimal("100000")); expense.setPayer("customer");
        IncidentResponse result = service.addExpense(7, expense, "admin", "ADMIN");
        assertThat(result.getExpenses().get(0).get("payer")).isEqualTo("carrier");
        assertThat(result.getActualCost()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void rejectsExpenseAboveSupportedPaymentLimitBeforeSaving() {
        Incident incident = healthIncident(true);
        var expense = new com.example.racehorse_transport.dto.incident.IncidentExpenseRequest();
        expense.setCategory("vet_fee"); expense.setLabel("Khám tại trạm"); expense.setPhoto("invoice.jpg");
        expense.setAmount(new BigDecimal("10000000000000000")); expense.setPayer("customer");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.addExpense(7, expense, "admin", "ADMIN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("giới hạn thanh toán");
        assertThat(incident.getExpensesJson()).isNull();
    }

    @Test
    void deletingIncidentExpenseRemovesItAndRecalculatesCustomerTotal() {
        Incident incident = healthIncident(true);
        incident.setExpensesJson("""
                [{"id":"expense-1","label":"Thuốc","amount":300000,"payer":"customer"},
                 {"id":"expense-2","label":"Vật tư","amount":200000,"payer":"carrier"}]
                """);
        incident.setActualCost(new BigDecimal("300000"));
        incident.setCostBearer("CUSTOMER");

        IncidentResponse response = service.deleteExpense(7, "expense-1", "admin", "ADMIN");

        assertThat(response.getExpenses()).hasSize(1);
        assertThat(response.getExpenses().get(0).get("id")).isEqualTo("expense-2");
        assertThat(incident.getActualCost()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(incident.getCostBearer()).isEqualTo("COMPANY");
    }

    @Test
    void deletingUnknownIncidentExpenseFails() {
        Incident incident = healthIncident(true);
        incident.setExpensesJson("[{\"id\":\"expense-1\",\"label\":\"Thuốc\",\"amount\":300000,\"payer\":\"customer\"}]");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.deleteExpense(7, "missing", "admin", "ADMIN"))
                .isInstanceOf(com.example.racehorse_transport.exception.ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy khoản chi phí");
        assertThat(incident.getExpensesJson()).contains("expense-1");
    }

    @Test
    void genericCompletionCannotBypassHorseHealthWorkflow() {
        healthIncident(true);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.completeResolution(7,
                new com.example.racehorse_transport.dto.incident.ResolveIncidentRequest(), "coordinator"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("hộ tống");
    }

    @Test
    void noBookingsMeansNoQuery() {
        assertThat(service.getIncidentsForBookings(List.of())).isEmpty();
        verifyNoInteractions(incidentRepository);
    }
}
