package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.regulation.RegulationPreviewResponseDto;
import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.entity.enums.AddressRole;
import com.example.racehorse_transport.entity.enums.BookingHorseStatus;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.entity.enums.DocumentSubmitter;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.DocumentCompletenessPolicy;
import com.example.racehorse_transport.repository.*;
import com.example.racehorse_transport.service.DocumentCompletenessService;
import com.example.racehorse_transport.service.RegulationService;
import static com.example.racehorse_transport.policy.BookingSubmissionPolicy.businessDate;
import java.time.Clock;
import java.util.Set;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentCompletenessServiceImpl implements DocumentCompletenessService {
    private final BookingRepository bookings;
    private final BookingAddressRepository addresses;
    private final BookingHorseRepository horses;
    private final DocumentRepository documents;
    private final RegulationService regulations;
    private final Clock clock;

    @Override
    public boolean isComplete(Long bookingId) {
        return isComplete(bookingId, null, null);
    }

    @Override
    public boolean isCompleteForQuoting(Long bookingId) {
        return isComplete(bookingId, DocumentSubmitter.CUSTOMER, null);
    }

    @Override
    public Set<DocumentSubmitter> missingSubmitters(Long bookingId) {
        Set<DocumentSubmitter> missing = java.util.EnumSet.noneOf(DocumentSubmitter.class);
        isComplete(bookingId, null, missing);
        return missing;
    }

    private boolean isComplete(Long bookingId, DocumentSubmitter submitter, Set<DocumentSubmitter> missing) {
        Booking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn."));
        if (booking.getBookingType() == BookingType.LOCAL) {
            return true;
        }
        RegulationPreviewResponseDto resolved = resolve(booking);
        List<BookingHorse> active = horses.findForDetail(bookingId).stream()
                .filter(horse -> horse.getStatus() == BookingHorseStatus.ACTIVE).toList();
        var rows = findCandidateDocuments(bookingId);
        var requiredOn = DocumentCompletenessPolicy.requiredOn(booking, businessDate(clock.instant()));
        if (missing != null) {
            resolved.requirements().stream().map(requirement -> requirement.submitterType()).distinct()
                    .filter(side -> !DocumentCompletenessPolicy.isComplete(
                            bookingId, active, resolved.requirements(), rows, requiredOn, side))
                    .forEach(missing::add);
        }
        return !resolved.requirementsDependOnGate() && DocumentCompletenessPolicy.isComplete(
                bookingId, active, resolved.requirements(), rows, requiredOn, submitter);
    }

    @Override
    public RegulationPreviewResponseDto resolve(Booking booking) {
        List<BookingAddress> rows = addresses.findAddressesForDetail(booking.getId());
        Departure departure = booking.getDeparture();
        Long gate = departure == null ? null : regulations.gateOf(departure.getRoute(), booking.getBookingType());
        return regulations.resolve(booking.getBookingType(), countryOf(rows, AddressRole.PICKUP),
                countryOf(rows, AddressRole.DELIVERY), booking.getRequestedPickupDate(), gate);
    }

    @Override
    public List<Document> findCandidateDocuments(Long bookingId) {
        return Stream.concat(documents.findForBookingReview(bookingId).stream(),
                documents.findApprovedHorseDocumentsWithoutRequirement(bookingId).stream()).toList();
    }

    @Override
    public long redoLeadDays(Booking booking, RegulationPreviewResponseDto resolved, java.time.LocalDate on) {
        List<BookingHorse> active = horses.findForDetail(booking.getId()).stream()
                .filter(horse -> horse.getStatus() == BookingHorseStatus.ACTIVE).toList();
        return com.example.racehorse_transport.policy.RegulationPolicy.redoLeadDays(resolved.rule(),
                DocumentCompletenessPolicy.missingTypes(booking.getId(), active, resolved.requirements(),
                        findCandidateDocuments(booking.getId()), on));
    }

    private static String countryOf(List<BookingAddress> rows, AddressRole role) {
        return rows.stream().filter(row -> row.getAddressType() == role).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa chỉ của đơn."))
                .getCountry().getCode();
    }
}
