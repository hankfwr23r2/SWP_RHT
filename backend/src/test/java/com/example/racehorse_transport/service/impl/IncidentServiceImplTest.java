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
    void noBookingsMeansNoQuery() {
        assertThat(service.getIncidentsForBookings(List.of())).isEmpty();
        verifyNoInteractions(incidentRepository);
    }
}
