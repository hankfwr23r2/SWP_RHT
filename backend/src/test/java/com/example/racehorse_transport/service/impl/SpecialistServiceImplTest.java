package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.BookingResponse;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.repository.RegulationRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.TripDocumentRepository;
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
    private final TripDocumentRepository tripDocumentRepository = mock(TripDocumentRepository.class);
    private final RegulationRepository regulationRepository = mock(RegulationRepository.class);
    private final LocationRepository locationRepository = mock(LocationRepository.class);

    private SpecialistServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SpecialistServiceImpl(
                bookingRepository,
                staffRepository,
                tripDocumentRepository,
                regulationRepository,
                locationRepository,
                new ObjectMapper());

        Staff specialist = new Staff();
        specialist.setRole("SPECIALIST");
        specialist.setStaffCode("SP-01");
        when(staffRepository.findByUser_Username("specialist")).thenReturn(Optional.of(specialist));
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
