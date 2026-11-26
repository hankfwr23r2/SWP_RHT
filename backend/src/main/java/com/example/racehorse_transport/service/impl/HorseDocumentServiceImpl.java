package com.example.racehorse_transport.service.impl;

import static com.example.racehorse_transport.policy.BookingSubmissionPolicy.businessDate;
import static java.util.stream.Collectors.groupingBy;

import com.example.racehorse_transport.dto.horse.HorseDocumentResponseDto;
import com.example.racehorse_transport.dto.horse.HorseDocumentUploadRequestDto;
import com.example.racehorse_transport.dto.regulation.DocumentTypeResponseDto;
import com.example.racehorse_transport.entity.Document;
import com.example.racehorse_transport.entity.DocumentType;
import com.example.racehorse_transport.entity.Horse;
import com.example.racehorse_transport.entity.enums.DocumentSubject;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.repository.BookingHorseRepository;
import com.example.racehorse_transport.service.NotificationService;
import java.util.Set;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.DocumentReviewPolicy;
import com.example.racehorse_transport.repository.DocumentRepository;
import com.example.racehorse_transport.repository.DocumentTypeRepository;
import com.example.racehorse_transport.repository.HorseRepository;
import com.example.racehorse_transport.service.DocumentService;
import com.example.racehorse_transport.service.HorseDocumentService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HorseDocumentServiceImpl implements HorseDocumentService {
    private final HorseRepository horses;
    private final DocumentTypeRepository types;
    private final DocumentRepository documents;
    private final DocumentService attachments;
    private final Clock clock;
    private final BookingHorseRepository bookingHorses;
    private final NotificationService notifications;

    @Override
    @Transactional(readOnly = true)
    public List<DocumentTypeResponseDto> documentTypes() {
        return DocumentTypeResponseDto.fromList(types.findByOwnerTypeOrderById(DocumentSubject.HORSE));
    }

    @Override
    @Transactional(readOnly = true)
    public List<HorseDocumentResponseDto> documents(String username, Long horseId) {
        ownedHorse(username, horseId);
        return documentsFor(List.of(horseId)).get(horseId);
    }

    @Override
    @Transactional
    public HorseDocumentResponseDto upload(String username, Long horseId, HorseDocumentUploadRequestDto request) {
        Horse horse = ownedHorse(username, horseId);
        Long id = attachments.attachForReview(request.file(), request.documentTypeCode(), DocumentSubject.HORSE, horseId,
                horse.getOwner().getUserId(), null, businessDate(clock.instant()));
        Document document = documents.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giấy tờ vừa tải."));
        var reviewable = Set.of(BookingStatus.UNDER_REVIEW, BookingStatus.NEEDS_RESUBMISSION,
                BookingStatus.QUOTING, BookingStatus.AWAITING_DEPOSIT, BookingStatus.CONFIRMED);
        for (var booking : bookingHorses.findAssignedBookingsForHorse(horseId, reviewable)) {
            notifications.notify(List.of(booking.getSpecialist().getUserId()), booking.getId(), "Khách đã nộp giấy tờ",
                    horse.getName() + " · " + document.getDocumentType().getName(),
                    "/specialist/verification/" + booking.getId());
        }
        return HorseDocumentResponseDto.from(document.getDocumentType(), document, document.getStatus(), false);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, List<HorseDocumentResponseDto>> documentsFor(List<Long> horseIds) {
        if (horseIds.isEmpty()) {
            return Map.of();
        }
        List<DocumentType> catalog = types.findByOwnerTypeOrderById(DocumentSubject.HORSE);
        var byHorseAndType = documents.findForHorseProfiles(horseIds).stream().collect(
                groupingBy(document -> document.getHorse().getId(), groupingBy(document -> document.getDocumentType().getId())));
        LocalDate today = businessDate(clock.instant());
        return horseIds.stream().distinct().collect(Collectors.toMap(Function.identity(), id -> {
            var byType = byHorseAndType.getOrDefault(id, Map.of());
            return catalog.stream().map(type -> profileDocument(type, byType.getOrDefault(type.getId(), List.of()), today)).toList();
        }));
    }

    private static HorseDocumentResponseDto profileDocument(DocumentType type, List<Document> versions, LocalDate today) {
        Document latest = versions.isEmpty() ? null : versions.getFirst();
        boolean canUpload = versions.stream().noneMatch(document -> DocumentReviewPolicy.blocksReplacement(document, null, today));
        return HorseDocumentResponseDto.from(type, latest, latest == null ? null : DocumentReviewPolicy.statusOn(latest, today), canUpload);
    }

    private Horse ownedHorse(String username, Long id) {
        return horses.findByIdAndOwner_User_Username(id, username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ ngựa."));
    }
}
