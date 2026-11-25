package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.operation.CrewTripResponseDto;
import com.example.racehorse_transport.dto.operation.HandoverPaperResponseDto;
import com.example.racehorse_transport.entity.Document;
import com.example.racehorse_transport.entity.Handover;
import com.example.racehorse_transport.entity.HandoverDocument;
import com.example.racehorse_transport.entity.HandoverDocumentId;
import com.example.racehorse_transport.entity.enums.HandoverDocumentDirection;
import com.example.racehorse_transport.entity.enums.HandoverType;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.repository.HandoverDocumentRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.service.HandoverPaperService;
import com.example.racehorse_transport.service.HandoverService;
import com.example.racehorse_transport.repository.HandoverHorseRepository;
import com.example.racehorse_transport.entity.HandoverHorseId;
import com.example.racehorse_transport.service.HandoverEvidenceService;
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
    private final HandoverDocumentRepository papers;
    private final StaffRepository staff;
    private final HandoverEvidenceService evidence;
    private final HandoverHorseRepository horses;

    @Override
    @Transactional
    public CrewTripResponseDto attachRecord(Long handoverId, MultipartFile file, String username) {
        Handover handover = handovers.pendingForCrew(handoverId, username);
        Long actor = staff.findByUser_Username(username).orElseThrow(() -> new AccessDeniedException("Không tìm thấy hồ sơ nhân viên.")).getUserId();
        evidence.attachRecord(handover.getId(), file, actor);
        return handovers.crewTrip(handover.getTripStop().getVehicleTrip().getId(), username);
    }

    @Override
    @Transactional
    public CrewTripResponseDto attachPhoto(Long handoverId, Long horseLegId, MultipartFile file, String username) {
        Handover handover = handovers.pendingForCrew(handoverId, username);
        var horse = horses.findById(new HandoverHorseId(handoverId, horseLegId))
                .filter(row -> Boolean.TRUE.equals(row.getMicrochipMatched()))
                .orElseThrow(() -> new DispatchRuleException("horseLegId", "Cần quét đúng chip trước khi tải ảnh."));
        Long actor = staff.findByUser_Username(username)
                .orElseThrow(() -> new AccessDeniedException("Không tìm thấy hồ sơ nhân viên.")).getUserId();
        evidence.attachPhoto(horse, file, actor);
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
    public List<HandoverPaperResponseDto> markPaper(Long handoverId, Long documentId, boolean originalMissing, String username) {
        Handover handover = handovers.pendingForCrew(handoverId, username);
        HandoverDocumentDirection direction = directionOf(handover);
        if (originalMissing && direction != HandoverDocumentDirection.RECEIVED) {
            throw new DispatchRuleException("originalMissing", "Giấy đã nhận phải được trả.", true);
        }
        Document paper = originals(handover).stream().filter(d -> d.getId().equals(documentId)).findFirst()
                .orElseThrow(() -> new DispatchRuleException("documentId", "Giấy này không thuộc các đơn trong lần giao nhận.", true));
        HandoverDocumentId id = new HandoverDocumentId(handover.getId(), paper.getId());
        HandoverDocument row = papers.findById(id).orElseGet(() -> {
            HandoverDocument created = new HandoverDocument();
            created.setId(id);
            created.setHandover(handover);
            created.setDocument(paper);
            return created;
        });
        row.setDirection(originalMissing ? HandoverDocumentDirection.NOT_RECEIVED : direction);
        papers.saveAndFlush(row);
        return describe(handover);
    }

    // Nhận từ khách (đón) hoặc từ đại lý (cửa khẩu, đơn nhập) thì nhận giấy gốc; giao cho người nhận thì trả giấy gốc
    private static HandoverDocumentDirection directionOf(Handover handover) {
        if (handover.getHandoverType() == HandoverType.PICKUP || handover.getHandoverType() == HandoverType.AGENT && handover.getFromParty() != null) {
            return HandoverDocumentDirection.RECEIVED;
        }
        if (handover.getHandoverType() == HandoverType.DELIVERY || handover.getHandoverType() == HandoverType.AGENT) {
            return HandoverDocumentDirection.RETURNED;
        }
        throw new DispatchRuleException("handoverType", "Lần giao nhận này không nhận hay trả giấy gốc.", true);
    }

    // Giấy bắt buộc đã duyệt của các đơn có ngựa trong lần giao nhận
    private List<Document> originals(Handover handover) {
        return evidence.originals(handover.getId(), handovers.bookingIdsOf(handover),
                directionOf(handover) == HandoverDocumentDirection.RECEIVED);
    }

    private List<HandoverPaperResponseDto> describe(Handover handover) {
        Map<Long, HandoverDocumentDirection> handled = papers.findById_HandoverId(handover.getId()).stream()
                .collect(Collectors.toMap(p -> p.getId().getDocumentId(), HandoverDocument::getDirection));
        return originals(handover).stream()
                .map(document -> HandoverPaperResponseDto.from(document, bookingCode(document), handled.get(document.getId()))).toList();
    }

    private static String bookingCode(Document d) {
        if (d.getBooking() != null) return d.getBooking().getCode();
        return d.getBookingHorse() != null ? d.getBookingHorse().getBooking().getCode() : null;
    }
}
