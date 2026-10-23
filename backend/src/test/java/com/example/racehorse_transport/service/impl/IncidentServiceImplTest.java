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
}
