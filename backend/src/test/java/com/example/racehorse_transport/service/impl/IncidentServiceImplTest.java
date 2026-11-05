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

    private final IncidentServiceImpl service = new IncidentServiceImpl(
            mock(com.example.racehorse_transport.service.CaptureService.class),
            incidentRepository,
            mock(BookingRepository.class),
            mock(BookingAssignmentRepository.class),
            mock(StaffRepository.class),
            mock(SystemLogRepository.class));

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

    @Test
    void noBookingsMeansNoQuery() {
        assertThat(service.getIncidentsForBookings(List.of())).isEmpty();
        verifyNoInteractions(incidentRepository);
    }
}
