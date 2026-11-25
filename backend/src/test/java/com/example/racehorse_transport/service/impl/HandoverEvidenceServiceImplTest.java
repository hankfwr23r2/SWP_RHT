package com.example.racehorse_transport.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.racehorse_transport.dto.operation.DeliveryRecipientRequestDto;
import com.example.racehorse_transport.dto.operation.HandoverPaperRequestDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Document;
import com.example.racehorse_transport.entity.DocumentType;
import com.example.racehorse_transport.entity.Handover;
import com.example.racehorse_transport.entity.HandoverDocument;
import com.example.racehorse_transport.entity.HandoverHorse;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.enums.DocumentStatus;
import com.example.racehorse_transport.entity.enums.DocumentSubject;
import com.example.racehorse_transport.entity.enums.HandoverDocumentDirection;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.repository.DocumentRepository;
import com.example.racehorse_transport.repository.HandoverDocumentRepository;
import com.example.racehorse_transport.service.CollectionCodeService;
import com.example.racehorse_transport.service.DocumentService;
import com.example.racehorse_transport.service.NotificationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

class HandoverEvidenceServiceImplTest {
    private final DocumentService files = mock(DocumentService.class);
    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final HandoverDocumentRepository papers = mock(HandoverDocumentRepository.class);
    private final CollectionCodeService codes = mock(CollectionCodeService.class);
    private final com.example.racehorse_transport.repository.HandoverHorseRepository handoverHorses =
            mock(com.example.racehorse_transport.repository.HandoverHorseRepository.class);
    private final com.example.racehorse_transport.repository.HorseLegRepository legs =
            mock(com.example.racehorse_transport.repository.HorseLegRepository.class);
    private final HandoverEvidenceServiceImpl evidence = new HandoverEvidenceServiceImpl(
            files, documents, papers, codes, mock(NotificationService.class),
            handoverHorses, legs);

    @Test
    void conditionAndPhotoAreCheckedForTheSameHorse() {
        Handover handover = new Handover();
        handover.setId(7L);
        HorseLeg leg = new HorseLeg();
        leg.setId(8L);
        HandoverHorse horse = new HandoverHorse();
        horse.setHandover(handover);
        horse.setHorseLeg(leg);

        assertThatThrownBy(() -> evidence.requireHorseEvidence(horse))
                .isInstanceOfSatisfying(DispatchRuleException.class, e -> assertThat(e.getField()).isEqualTo("conditionNote"));
        horse.setConditionNote("Khỏe");
        assertThatThrownBy(() -> evidence.requireHorseEvidence(horse))
                .isInstanceOfSatisfying(DispatchRuleException.class, e -> assertThat(e.getField()).isEqualTo("photo"));

        var photo = new MockMultipartFile("file", "horse.jpg", "image/jpeg", new byte[]{1});
        Document uploaded = new Document();
        when(files.attach(photo, "HANDOVER_PHOTO", DocumentSubject.HANDOVER, 7L, 9L)).thenReturn(10L);
        when(documents.getReferenceById(10L)).thenReturn(uploaded);
        evidence.attachPhoto(horse, photo, 9L);
        assertThat(uploaded.getEvidenceHorseLeg()).isSameAs(leg);
        when(documents.existsByHandover_IdAndEvidenceHorseLeg_IdAndDocumentType_Code(7L, 8L, "HANDOVER_PHOTO")).thenReturn(true);
        assertThatCode(() -> evidence.requireHorseEvidence(horse)).doesNotThrowAnyException();
    }

    @Test
    void signedRecordRequiresAnImageOrPdf() {
        var text = new MockMultipartFile("record", "notes.txt", "text/plain", new byte[]{1});
        assertThatThrownBy(() -> evidence.attachRecord(7L, text, 9L))
                .isInstanceOfSatisfying(DispatchRuleException.class, e -> assertThat(e.getField()).isEqualTo("record"));

        var pdf = new MockMultipartFile("record", "signed.pdf", "application/pdf", new byte[]{1});
        evidence.attachRecord(7L, pdf, 9L);
        verify(files).attach(pdf, "HANDOVER_RECORD", DocumentSubject.HANDOVER, 7L, 9L);
    }

    @Test
    void hubReceiptRequiresEveryPaperAndCanRecordMissingOriginal() {
        Handover handover = new Handover();
        handover.setId(7L);
        Document document = new Document();
        document.setId(10L);
        document.setStatus(DocumentStatus.APPROVED);
        document.setDocumentType(new DocumentType());
        when(documents.findForBookingReview(11L)).thenReturn(List.of(document));
        assertThatThrownBy(() -> evidence.recordHubPapers(handover, 11L, true, List.of()))
                .isInstanceOf(DispatchRuleException.class);

        evidence.recordHubPapers(handover, 11L, true, List.of(new HandoverPaperRequestDto(10L, true)));
        var saved = ArgumentCaptor.forClass(HandoverDocument.class);
        verify(papers).save(saved.capture());
        assertThat(saved.getValue().getDirection()).isEqualTo(HandoverDocumentDirection.NOT_RECEIVED);
        assertThat(saved.getValue().getDocument()).isSameAs(document);
    }

    @Test
    void returnsHorsePapersAtTheirOwnStopAndBookingPapersAtTheLastStop() {
        var booking = new Booking();
        booking.setId(11L);
        var trip = new com.example.racehorse_transport.entity.VehicleTrip();
        trip.setId(21L);
        var first = deliveryLeg(booking, trip, 31L, 2);
        var second = deliveryLeg(booking, trip, 32L, 3);
        var firstPaper = new Document();
        firstPaper.setId(41L);
        firstPaper.setBookingHorse(first.getBookingHorse());
        var secondPaper = new Document();
        secondPaper.setId(42L);
        secondPaper.setBookingHorse(second.getBookingHorse());
        var bookingPaper = new Document();
        bookingPaper.setId(43L);
        bookingPaper.setBooking(booking);

        when(handoverHorses.findById_HandoverId(1L)).thenReturn(List.of(handoverHorse(first)));
        when(handoverHorses.findById_HandoverId(2L)).thenReturn(List.of(handoverHorse(second)));
        when(legs.findActiveOnTrip(21L, java.util.Set.of(com.example.racehorse_transport.entity.enums.HorseLegStatus.CANCELLED)))
                .thenReturn(List.of(first, second));
        when(documents.findOriginalsEverReceived(11L)).thenReturn(List.of(firstPaper, secondPaper, bookingPaper));
        var received = new HandoverDocument();
        received.setDirection(HandoverDocumentDirection.RECEIVED);
        for (long id : List.of(41L, 42L, 43L)) {
            when(papers.findById_DocumentIdAndHandover_StatusOrderByHandover_HandedAtDescHandover_IdDesc(
                    id, com.example.racehorse_transport.entity.enums.HandoverStatus.DONE)).thenReturn(List.of(received));
        }

        assertThat(evidence.originals(1L, java.util.Set.of(11L), false)).containsExactly(firstPaper);
        assertThat(evidence.originals(2L, java.util.Set.of(11L), false)).containsExactly(secondPaper, bookingPaper);
    }

    private static HorseLeg deliveryLeg(Booking booking, com.example.racehorse_transport.entity.VehicleTrip trip,
                                        long horseId, int sequence) {
        var horse = new com.example.racehorse_transport.entity.BookingHorse();
        horse.setId(horseId);
        horse.setBooking(booking);
        var stop = new com.example.racehorse_transport.entity.TripStop();
        stop.setSequenceNumber(sequence);
        var leg = new HorseLeg();
        leg.setLegType(com.example.racehorse_transport.entity.enums.HorseLegType.DELIVERY);
        leg.setBookingHorse(horse);
        leg.setVehicleTrip(trip);
        leg.setAlightTripStop(stop);
        return leg;
    }

    private static HandoverHorse handoverHorse(HorseLeg leg) {
        var row = new HandoverHorse();
        row.setHorseLeg(leg);
        return row;
    }

    @Test
    void recipientNameIsRequiredWithEitherCodeOrCustomerConfirmation() {
        Booking booking = new Booking();
        booking.setId(11L);
        var proof = new DeliveryRecipientRequestDto("  Người nhận  ", "012345");
        assertThatThrownBy(() -> evidence.requireRecipient(booking, proof)).isInstanceOf(DispatchRuleException.class);
        when(codes.verify(11L, "012345")).thenReturn(true);
        assertThat(evidence.requireRecipient(booking, proof)).isEqualTo("Người nhận");
        booking.setCollectionConfirmed(true);
        assertThatThrownBy(() -> evidence.requireRecipient(booking, null)).isInstanceOf(DispatchRuleException.class);
        assertThat(evidence.requireRecipient(booking, new DeliveryRecipientRequestDto("Người nhận", null))).isEqualTo("Người nhận");
    }
}
