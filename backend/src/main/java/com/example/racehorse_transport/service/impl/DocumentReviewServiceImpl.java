package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.BookingDocumentResponseDto;
import com.example.racehorse_transport.dto.booking.BookingDocumentChecklistResponseDto;
import com.example.racehorse_transport.dto.booking.BookingDocumentChecklistResponseDto.Item;
import com.example.racehorse_transport.policy.DocumentCompletenessPolicy;
import java.time.LocalDate;
import static com.example.racehorse_transport.policy.BookingSubmissionPolicy.businessDate;
import com.example.racehorse_transport.dto.booking.DocumentUploadRequestDto;
import com.example.racehorse_transport.dto.booking.PartnerDocumentUploadRequestDto;
import com.example.racehorse_transport.dto.booking.DocumentApprovalRequestDto;
import com.example.racehorse_transport.dto.regulation.DocumentRequirementResponseDto;
import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.entity.enums.*;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.BookingReviewPolicy;
import com.example.racehorse_transport.policy.DocumentReviewPolicy;
import com.example.racehorse_transport.repository.*;
import com.example.racehorse_transport.service.*;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentReviewServiceImpl implements DocumentReviewService {
    private final BookingRepository bookings;
    private final BookingHorseRepository horses;
    private final DocumentRepository documents;
    private final DocumentRequirementRepository requirements;
    private final StaffRepository staff;
    private final DocumentService attachments;
    private final DocumentCompletenessService completeness;
    private final NotificationService notifications;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<BookingDocumentResponseDto> list(Long bookingId) {
        if (!bookings.existsById(bookingId)) {
            throw new ResourceNotFoundException("Không tìm thấy đơn.");
        }
        return documents.findForBookingReview(bookingId).stream().map(BookingDocumentResponseDto::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BookingDocumentChecklistResponseDto checklist(Long bookingId, String customerUsername) {
        Booking booking = (customerUsername == null ? bookings.findById(bookingId)
                : bookings.findByIdAndCustomer_User_Username(bookingId, customerUsername))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn."));
        var resolved = completeness.resolve(booking);
        var requirementIds = resolved.requirements().stream().map(DocumentRequirementResponseDto::id)
                .collect(java.util.stream.Collectors.toSet());
        List<Document> rows = documents.findForBookingReview(bookingId).stream()
                .filter(row -> requirementIds.contains(row.getDocumentRequirement().getId())).toList();
        List<BookingHorse> active = horses.findForDetail(bookingId).stream()
                .filter(horse -> horse.getStatus() == BookingHorseStatus.ACTIVE).toList();
        LocalDate today = businessDate(clock.instant());
        List<Item> items = resolved.requirements().stream().flatMap(requirement -> switch (requirement.ownerType()) {
            case HORSE, BOOKING_HORSE -> active.stream().map(horse -> checklistItem(requirement, bookingId, horse, rows, today));
            default -> java.util.stream.Stream.of(checklistItem(requirement, bookingId, null, rows, today));
        }).toList();
        return new BookingDocumentChecklistResponseDto(resolved.requirementsDependOnGate(), items,
                rows.stream().map(row -> BookingDocumentResponseDto.from(row, bookingHorseId(row, active))).toList());
    }

    private static Long bookingHorseId(Document row, List<BookingHorse> active) {
        if (row.getBookingHorse() != null) {
            return row.getBookingHorse().getId();
        }
        return row.getHorse() == null ? null : active.stream()
                .filter(horse -> horse.getHorse().getId().equals(row.getHorse().getId()))
                .map(BookingHorse::getId).findFirst().orElse(null);
    }

    private static Item checklistItem(DocumentRequirementResponseDto requirement, Long bookingId, BookingHorse horse,
            List<Document> rows, LocalDate today) {
        boolean satisfied = rows.stream().anyMatch(row ->
                DocumentCompletenessPolicy.satisfies(row, requirement, bookingId, horse, today));
        return Item.from(requirement, horse == null ? null : horse.getId(), satisfied,
                DocumentReviewPolicy.requiredForQuoting(requirement), Boolean.TRUE.equals(requirement.mandatory()));
    }

    @Override
    @Transactional
    public BookingDocumentResponseDto attach(Long bookingId, String username, DocumentUploadRequestDto request) {
        Booking booking = lockBooking(bookingId);
        AppUser customer = booking.getCustomer().getUser();
        if (!customer.getUsername().equals(username)) {
            throw new ResourceNotFoundException("Không tìm thấy đơn.");
        }
        DocumentRequirementResponseDto requirement = requirement(booking, request.requirementId());
        DocumentReviewPolicy.requireCustomerUpload(booking.getStatus(), requirement);
        return attach(bookingId, request, requirement, customer.getId());
    }

    @Override
    @Transactional
    public BookingDocumentResponseDto attachForPartner(Long bookingId, String username, PartnerDocumentUploadRequestDto request) {
        Booking booking = lockBooking(bookingId);
        DocumentRequirementResponseDto requirement = requirement(booking, request.requirementId());
        DocumentReviewPolicy.requirePartnerUpload(booking.getStatus(), requirement.submitterType());
        return attach(bookingId, request.toUploadRequest(), requirement, reviewer(username).getUserId());
    }

    private DocumentRequirementResponseDto requirement(Booking booking, Long requirementId) {
        return completeness.resolve(booking).requirements().stream()
                .filter(row -> row.id().equals(requirementId))
                .findFirst().orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu giấy tờ của đơn."));
    }

    private BookingDocumentResponseDto attach(Long bookingId, DocumentUploadRequestDto request,
            DocumentRequirementResponseDto requirement, Long uploaderId) {
        BookingHorse horse = ownerHorse(bookingId, request.bookingHorseId(), requirement.ownerType());
        Long owner = switch (requirement.ownerType()) {
            case HORSE -> horse.getHorse().getId();
            case BOOKING_HORSE -> horse.getId();
            default -> bookingId;
        };
        LocalDate today = businessDate(clock.instant());
        if (documents.findForBookingReview(bookingId).stream().anyMatch(document ->
                DocumentCompletenessPolicy.matchesOwnerAndRequirement(document, requirement, bookingId, horse)
                        && (document.getStatus() == DocumentStatus.UPLOADED
                                || DocumentCompletenessPolicy.satisfies(document, requirement, bookingId, horse, today)))) {
            throw BookingRuleException.conflict("Giấy tờ của yêu cầu này đang chờ duyệt hoặc còn hiệu lực.");
        }
        Long id = attachments.attach(request.file(), requirement.documentTypeCode(), requirement.ownerType(),
                owner, uploaderId);
        Document document = documents.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giấy tờ vừa tải."));
        request.applyTo(document, requirements.getReferenceById(requirement.id()));
        return BookingDocumentResponseDto.from(document);
    }

    @Override
    @Transactional
    public BookingDocumentResponseDto approve(Long bookingId, Long documentId, String username, DocumentApprovalRequestDto request) {
        String note = request == null || request.note() == null || request.note().isBlank() ? null : request.note().strip();
        return conclude(bookingId, documentId, username, DocumentStatus.APPROVED, note);
    }

    @Override
    @Transactional
    public BookingDocumentResponseDto reject(Long bookingId, Long documentId, String username, String reason) {
        return conclude(bookingId, documentId, username, DocumentStatus.REJECTED, BookingReviewPolicy.requireReason(reason));
    }

    private BookingDocumentResponseDto conclude(Long bookingId, Long documentId, String username,
            DocumentStatus outcome, String note) {
        Booking booking = lockBooking(bookingId);
        DocumentReviewPolicy.requireStaffReview(booking.getStatus());
        Document document = documents.findForReviewUpdate(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giấy tờ."));
        if (documents.findForBookingReview(bookingId).stream().noneMatch(row -> row.getId().equals(documentId))
                || completeness.resolve(booking).requirements().stream()
                        .noneMatch(row -> row.id().equals(document.getDocumentRequirement().getId())
                                && row.documentTypeId().equals(document.getDocumentType().getId()))) {
            throw new ResourceNotFoundException("Không tìm thấy giấy tờ của đơn.");
        }
        DocumentReviewPolicy.requireUploaded(document.getStatus());
        document.setReviewedBy(reviewer(username));
        document.setReviewedAt(clock.instant());
        document.setReviewNote(note);
        document.setStatus(outcome);
        if (outcome == DocumentStatus.REJECTED) {
            notifications.notify(List.of(booking.getCustomer().getUser().getId()), bookingId,
                    "Giấy tờ cần bổ sung", note, null);
        }
        return BookingDocumentResponseDto.from(document);
    }

    private Staff reviewer(String username) {
        return staff.findByUser_Username(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ nhân viên."));
    }

    private Booking lockBooking(Long id) {
        return bookings.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn."));
    }

    private BookingHorse ownerHorse(Long bookingId, Long horseId, DocumentSubject subject) {
        if (subject == DocumentSubject.BOOKING && horseId == null) {
            return null;
        }
        if (horseId == null || (subject != DocumentSubject.HORSE && subject != DocumentSubject.BOOKING_HORSE)) {
            throw new BookingRuleException("Chủ sở hữu không khớp yêu cầu giấy tờ.");
        }
        return horses.findById(horseId)
                .filter(row -> row.getBooking().getId().equals(bookingId) && row.getStatus() == BookingHorseStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa đang hoạt động trên đơn."));
    }
}
