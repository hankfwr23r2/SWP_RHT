package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.operation.CrewTripResponseDto;
import com.example.racehorse_transport.dto.operation.HandoverPaperResponseDto;
import com.example.racehorse_transport.entity.Document;
import com.example.racehorse_transport.entity.Handover;
import com.example.racehorse_transport.entity.HandoverDocument;
import com.example.racehorse_transport.entity.HandoverDocumentId;
import com.example.racehorse_transport.entity.enums.DocumentStatus;
import com.example.racehorse_transport.entity.enums.DocumentSubject;
import com.example.racehorse_transport.entity.enums.HandoverDocumentDirection;
import com.example.racehorse_transport.entity.enums.HandoverType;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.repository.DocumentRepository;
import com.example.racehorse_transport.repository.HandoverDocumentRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.service.DocumentService;
import com.example.racehorse_transport.service.HandoverPaperService;
import com.example.racehorse_transport.service.HandoverService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class HandoverPaperServiceImpl implements HandoverPaperService {

    private final HandoverService handovers;
    private final DocumentService documentFiles;
    private final DocumentRepository documents;
    private final HandoverDocumentRepository papers;
    private final StaffRepository staff;

    @Override
    @Transactional
    public CrewTripResponseDto attachRecord(Long handoverId, MultipartFile file, String username) {
        Handover handover = handovers.pendingForCrew(handoverId, username);
        Long actor = staff.findByUser_Username(username).orElseThrow(() -> new AccessDeniedException("Không tìm thấy hồ sơ nhân viên.")).getUserId();
        documentFiles.attach(file, HandoverServiceImpl.RECORD_TYPE, DocumentSubject.HANDOVER, handover.getId(), actor);
        return handovers.crewTrip(handover.getTripStop().getVehicleTrip().getId(), username);
    }

    @Override
    // Không đặt readOnly: pendingForCrew khóa lần giao nhận, PostgreSQL từ chối khóa trong giao dịch chỉ đọc
    @Transactional
    public List<HandoverPaperResponseDto> papers(Long handoverId, String username) {
        Handover handover = handovers.pendingForCrew(handoverId, username);
        return describe(handover);
    }

    @Override
    @Transactional
    public List<HandoverPaperResponseDto> markPaper(Long handoverId, Long documentId, String username) {
        Handover handover = handovers.pendingForCrew(handoverId, username);
        HandoverDocumentDirection direction = directionOf(handover);
        Document paper = originals(handover).stream().filter(d -> d.getId().equals(documentId)).findFirst()
                .orElseThrow(() -> new DispatchRuleException("documentId", "Giấy này không thuộc các đơn trong lần giao nhận.", true));
        HandoverDocumentId id = new HandoverDocumentId(handover.getId(), paper.getId());
        if (!papers.existsById(id)) {
            HandoverDocument row = new HandoverDocument();
            row.setId(id);
            row.setHandover(handover);
            row.setDocument(paper);
            row.setDirection(direction);
            papers.saveAndFlush(row);
        }
        return describe(handover);
    }

    // Nhận từ khách (đón) hoặc từ đại lý (cửa khẩu, đơn nhập) thì nhận giấy gốc; giao cho người nhận thì trả giấy gốc
    private static HandoverDocumentDirection directionOf(Handover handover) {
        if (handover.getHandoverType() == HandoverType.PICKUP || handover.getHandoverType() == HandoverType.AGENT && handover.getFromParty() != null) {
            return HandoverDocumentDirection.RECEIVED;
        }
        if (handover.getHandoverType() == HandoverType.DELIVERY) {
            return HandoverDocumentDirection.RETURNED;
        }
        throw new DispatchRuleException("handoverType", "Lần giao nhận này không nhận hay trả giấy gốc.", true);
    }

    // Giấy bắt buộc đã duyệt của các đơn có ngựa trong lần giao nhận
    private List<Document> originals(Handover handover) {
        return handovers.bookingIdsOf(handover).stream().flatMap(id -> documents.findForBookingReview(id).stream())
                .filter(d -> d.getStatus() == DocumentStatus.APPROVED).distinct().toList();
    }

    private List<HandoverPaperResponseDto> describe(Handover handover) {
        Map<Long, HandoverDocumentDirection> handled = papers.findById_HandoverId(handover.getId()).stream()
                .collect(Collectors.toMap(p -> p.getId().getDocumentId(), HandoverDocument::getDirection));
        return originals(handover).stream().map(d -> new HandoverPaperResponseDto(d.getId(), d.getDocumentType().getName(), ownerLabel(d),
                bookingCode(d), handled.get(d.getId()))).toList();
    }

    private static String ownerLabel(Document d) {
        if (d.getBookingHorse() != null) return d.getBookingHorse().getHorse().getName();
        if (d.getHorse() != null) return d.getHorse().getName();
        return d.getBooking() != null ? d.getBooking().getCode() : "";
    }

    private static String bookingCode(Document d) {
        if (d.getBooking() != null) return d.getBooking().getCode();
        return d.getBookingHorse() != null ? d.getBookingHorse().getBooking().getCode() : null;
    }
}
