package com.example.racehorse_transport.dto.complaint;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.entity.enums.ComplaintDecision;
import com.example.racehorse_transport.entity.enums.IncidentStatus;
import jakarta.validation.Validation;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class ComplaintDtoTest {

    @Test
    void managerRequestRequiresPhotosAndReportsThePhotoField() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (var request : List.of(new ComplaintOnBehalfRequestDto("Phone complaint", null, null),
                    new ComplaintOnBehalfRequestDto("Phone complaint", null, List.of()))) {
                assertThat(validator.validate(request)).extracting(violation -> violation.getPropertyPath().toString())
                        .containsExactly("photos");
            }
            MultipartFile photo = new MockMultipartFile("photos", "evidence.png", "image/png", new byte[] {1});
            assertThat(validator.validate(new ComplaintOnBehalfRequestDto("Phone complaint", null, List.of(photo))))
                    .isEmpty();
        }
    }

    @Test
    void complaintRequestsSetOnlyDescriptionAndLoadedRelationships() {
        var booking = new Booking();
        var horse = new BookingHorse();
        var customer = new ComplaintCreateRequestDto("  Damaged equipment  ", 1L, List.of());
        var manager = new ComplaintOnBehalfRequestDto("  Damaged equipment  ", 1L, List.of());

        for (var incident : List.of(customer.toEntity(booking, horse), manager.toEntity(booking, horse))) {
            assertThat(incident.getBooking()).isSameAs(booking);
            assertThat(incident.getBookingHorse()).isSameAs(horse);
            assertThat(incident.getDescription()).isEqualTo("Damaged equipment");
            assertThat(incident.getId()).isNull();
            assertThat(incident.getStatus()).isNull();
            assertThat(incident.getIncidentType()).isNull();
            assertThat(incident.getReportedBy()).isNull();
            assertThat(incident.getReportedAt()).isNull();
        }
    }

    @Test
    void replyAndConclusionTextDoNotSetWorkflowFields() {
        var incident = new Incident();
        incident.setStatus(IncidentStatus.REPORTED);
        new ComplaintReplyRequestDto("  Reply  ").applyTo(incident);
        new ComplaintResolveRequestDto(ComplaintDecision.ACCEPTED, "  Reason  ").applyTo(incident);

        assertThat(incident.getComplaintReply()).isEqualTo("Reply");
        assertThat(incident.getComplaintDecisionReason()).isEqualTo("Reason");
        assertThat(incident.getComplaintDecision()).isNull();
        assertThat(incident.getComplaintDecidedBy()).isNull();
        assertThat(incident.getResolvedAt()).isNull();
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.REPORTED);
    }

    @Test
    void responseUsesStoredFieldsAndServiceCalculatedPermissions() {
        var booking = new Booking();
        booking.setId(1L);
        var horse = new BookingHorse();
        horse.setId(2L);
        var incident = new Incident();
        incident.setId(3L);
        incident.setBookingHorse(horse);
        incident.setStatus(IncidentStatus.CLOSED);
        incident.setComplaintDecision(ComplaintDecision.ACCEPTED);
        var deliveredAt = Instant.parse("2026-11-25T08:00:00Z");

        var complaint = ComplaintResponseDto.from(incident, List.of());
        var response = DeliveryComplaintResponseDto.from(booking, deliveredAt, false, false, complaint);

        assertThat(response.bookingId()).isEqualTo(1L);
        assertThat(response.deliveredAt()).isEqualTo(deliveredAt);
        assertThat(response.customerCanRespond()).isFalse();
        assertThat(response.managerCanSubmit()).isFalse();
        assertThat(response.complaint().id()).isEqualTo(3L);
        assertThat(response.complaint().bookingHorseId()).isEqualTo(2L);
        assertThat(response.complaint().decision()).isEqualTo(ComplaintDecision.ACCEPTED);
    }
}
