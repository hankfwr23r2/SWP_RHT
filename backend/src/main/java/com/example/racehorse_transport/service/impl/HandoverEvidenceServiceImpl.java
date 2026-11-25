package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.operation.DeliveryRecipientRequestDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.HandoverHorse;
import com.example.racehorse_transport.entity.enums.DocumentSubject;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.repository.DocumentRepository;
import com.example.racehorse_transport.service.CollectionCodeService;
import com.example.racehorse_transport.service.DocumentService;
import com.example.racehorse_transport.service.HandoverEvidenceService;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.service.NotificationService;
import com.example.racehorse_transport.entity.Document;
import com.example.racehorse_transport.entity.Handover;
import com.example.racehorse_transport.entity.HandoverDocument;
import com.example.racehorse_transport.entity.HandoverDocumentId;
import com.example.racehorse_transport.entity.enums.DocumentStatus;
import com.example.racehorse_transport.entity.enums.HandoverDocumentDirection;
import com.example.racehorse_transport.entity.enums.HandoverStatus;
import com.example.racehorse_transport.repository.HandoverDocumentRepository;
import com.example.racehorse_transport.dto.operation.HandoverPaperRequestDto;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class HandoverEvidenceServiceImpl implements HandoverEvidenceService {
    private static final String PHOTO = "HANDOVER_PHOTO";
    private static final String RECORD = "HANDOVER_RECORD";

    private final DocumentService files;
    private final DocumentRepository documents;
    private final HandoverDocumentRepository papers;
    private final CollectionCodeService codes;
    private final NotificationService notifications;

    @Override
    @Transactional
    public void attachPhoto(HandoverHorse horse, MultipartFile photo, Long actor) {
        if (photo == null || photo.isEmpty() || photo.getContentType() == null || !photo.getContentType().startsWith("image/")) {
            throw new DispatchRuleException("photo", "Cần tải ảnh tình trạng ngựa.");
        }
        Long documentId = files.attach(photo, PHOTO, DocumentSubject.HANDOVER, horse.getHandover().getId(), actor);
        documents.getReferenceById(documentId).setEvidenceHorseLeg(horse.getHorseLeg());
    }

    @Override
    @Transactional
    public void attachRecord(Long handoverId, MultipartFile record, Long actor) {
        if (!isImageOrPdf(record)) {
            throw new DispatchRuleException("record", "Cần biên bản giao nhận đã ký.", true);
        }
        files.attach(record, RECORD, DocumentSubject.HANDOVER, handoverId, actor);
    }

    @Override
    @Transactional
    public void recordAgentDelivery(HorseLeg leg, MultipartFile proof, Long actor) {
        if (!isImageOrPdf(proof)) {
            throw new DispatchRuleException("proof", "Cần ảnh hoặc biên bản đại lý đã giao.", true);
        }
        files.attach(proof, "AGENT_DELIVERY_PROOF", DocumentSubject.HORSE_LEG, leg.getId(), actor);
        var line = leg.getBookingHorse();
        var booking = line.getBooking();
        notifications.notify(List.of(booking.getCustomer().getUser().getId()), booking.getId(),
                "Đại lý đã giao ngựa", "Đại lý đã giao " + line.getHorse().getName() + ".", "/orders/" + booking.getId());
    }

    private static boolean isImageOrPdf(MultipartFile file) {
        if (file == null || file.isEmpty()) return false;
        String type = file.getContentType();
        return type != null && (type.startsWith("image/") || type.equals("application/pdf"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Document> originals(Long handoverId, Set<Long> bookingIds, boolean receiving) {
        return bookingIds.stream().flatMap(id -> (receiving
                        ? documents.findForBookingReview(id) : documents.findOriginalsEverReceived(id)).stream())
                .distinct().filter(document -> receiving ? document.getStatus() == DocumentStatus.APPROVED
                        : heldOrReturnedHere(document, handoverId)).toList();
    }

    private boolean heldOrReturnedHere(Document document, Long handoverId) {
        if (handoverId != null && papers.existsById(new HandoverDocumentId(handoverId, document.getId()))) {
            return true;
        }
        return papers.findById_DocumentIdAndHandover_StatusOrderByHandover_HandedAtDescHandover_IdDesc(document.getId(), HandoverStatus.DONE)
                .stream().map(HandoverDocument::getDirection)
                .filter(direction -> direction != HandoverDocumentDirection.NOT_RECEIVED)
                .findFirst().orElse(null) == HandoverDocumentDirection.RECEIVED;
    }

    @Override
    @Transactional
    public void recordHubPapers(Handover handover, Long bookingId, boolean receiving, List<HandoverPaperRequestDto> selections) {
        var marked = new HashMap<Long, Boolean>();
        if (selections != null) {
            for (HandoverPaperRequestDto selection : selections) {
                if (selection == null || marked.putIfAbsent(selection.documentId(), selection.originalMissing()) != null) {
                    throw new DispatchRuleException("papers", "Giấy tờ gốc bị trùng hoặc không hợp lệ.");
                }
            }
        }
        List<Document> expected = originals(null, Set.of(bookingId), receiving);
        if (expected.size() != marked.size() || expected.stream().anyMatch(document ->
                !marked.containsKey(document.getId()) || !receiving && marked.get(document.getId()))) {
            throw new DispatchRuleException("papers", "Cần đánh dấu từng giấy tờ gốc trước khi hoàn tất.", true);
        }
        for (Document document : expected) {
            HandoverDocument paper = new HandoverDocument();
            paper.setId(new HandoverDocumentId(handover.getId(), document.getId()));
            paper.setHandover(handover);
            paper.setDocument(document);
            paper.setDirection(receiving ? (marked.get(document.getId()) ? HandoverDocumentDirection.NOT_RECEIVED
                    : HandoverDocumentDirection.RECEIVED) : HandoverDocumentDirection.RETURNED);
            papers.save(paper);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void requirePapers(Long handoverId, Set<Long> bookingIds, boolean receiving) {
        if (originals(handoverId, bookingIds, receiving).stream()
                .anyMatch(paper -> !papers.existsById(new HandoverDocumentId(handoverId, paper.getId())))) {
            throw new DispatchRuleException("papers", receiving
                    ? "Cần ghi nhận hoặc không nhận bản gốc của từng giấy."
                    : "Cần trả hết giấy tờ gốc đã nhận.", true);
        }
    }

    @Override
    @Transactional
    public void returnOriginals(Handover handover, Set<Long> bookingIds) {
        for (Document document : originals(null, bookingIds, false)) {
            HandoverDocument paper = new HandoverDocument();
            paper.setId(new HandoverDocumentId(handover.getId(), document.getId()));
            paper.setHandover(handover);
            paper.setDocument(document);
            paper.setDirection(HandoverDocumentDirection.RETURNED);
            papers.save(paper);
        }
    }

    @Override
    @Transactional
    public String requireRecipient(Booking booking, DeliveryRecipientRequestDto recipient) {
        if (recipient == null || recipient.recipientName() == null || recipient.recipientName().isBlank()) {
            throw new DispatchRuleException("recipientName", "Cần ghi họ tên người nhận.", true);
        }
        if (!booking.isCollectionConfirmed() && !codes.verify(booking.getId(), recipient.collectionCode())) {
            throw new DispatchRuleException("collectionCode", "Mã nhận ngựa không hợp lệ hoặc đã hết lượt nhập. Khách có thể xác nhận trên đơn.", true);
        }
        return recipient.recipientName().strip();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasRecord(Long handoverId) {
        return documents.existsByHandover_IdAndDocumentType_Code(handoverId, RECORD);
    }

    @Override
    @Transactional(readOnly = true)
    public void requireHorseEvidence(HandoverHorse horse) {
        if (horse.getConditionNote() == null || horse.getConditionNote().isBlank()) {
            throw new DispatchRuleException("conditionNote", "Cần ghi tình trạng từng con ngựa.");
        }
        if (!documents.existsByHandover_IdAndEvidenceHorseLeg_IdAndDocumentType_Code(
                horse.getHandover().getId(), horse.getHorseLeg().getId(), PHOTO)) {
            throw new DispatchRuleException("photo", "Cần ít nhất một ảnh cho từng con ngựa.");
        }
    }
}
