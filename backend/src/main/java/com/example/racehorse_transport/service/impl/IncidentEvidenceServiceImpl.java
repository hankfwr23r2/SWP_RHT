package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.incident.EvidenceResponseDto;
import com.example.racehorse_transport.entity.CostItem;
import com.example.racehorse_transport.entity.Document;
import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.enums.CostCategory;
import com.example.racehorse_transport.entity.enums.DocumentSubject;
import com.example.racehorse_transport.entity.enums.IncidentStatus;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.CostItemRepository;
import com.example.racehorse_transport.repository.DocumentRepository;
import com.example.racehorse_transport.repository.IncidentRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.service.DocumentService;
import com.example.racehorse_transport.service.IncidentEvidenceService;
import java.math.BigDecimal;
import java.time.Clock;
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
public class IncidentEvidenceServiceImpl implements IncidentEvidenceService {

    private static final String TOLL_RECEIPT = "TOLL_RECEIPT";
    private static final String EXPENSE_INVOICE = "EXPENSE_INVOICE";
    private static final String INCIDENT_PHOTO = "INCIDENT_PHOTO";

    private final DocumentService documentFiles;
    private final DocumentRepository documents;
    private final CostItemRepository costs;
    private final IncidentRepository incidents;
    private final StaffRepository staff;
    private final Clock clock;

    @Override
    @Transactional
    public List<EvidenceResponseDto> attachReceipt(Long costItemId, MultipartFile file, String username) {
        CostItem cost = costs.findById(costItemId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi phí " + costItemId));
        if (cost.getApprovedAt() != null) {
            throw new DispatchRuleException("costItemId", "Chi phí đã được duyệt, không thêm biên lai.", true);
        }
        String type = cost.getCategory() == CostCategory.TOLL ? TOLL_RECEIPT : EXPENSE_INVOICE;
        documentFiles.attach(file, type, DocumentSubject.COST_ITEM, cost.getId(), actor(username).getUserId());
        return receipts(costItemId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvidenceResponseDto> receipts(Long costItemId) {
        if (!costs.existsById(costItemId)) throw new ResourceNotFoundException("Không tìm thấy chi phí " + costItemId);
        return describe(documents.findByCostItem_IdOrderById(costItemId));
    }

    @Override
    @Transactional
    public List<EvidenceResponseDto> attachPhoto(Long incidentId, MultipartFile file, BigDecimal latitude, BigDecimal longitude, String documentType, String username) {
        Incident incident = incidents.findById(incidentId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự cố " + incidentId));
        if (incident.getStatus() == IncidentStatus.CLOSED) {
            throw new DispatchRuleException("incidentId", "Sự cố đã đóng, không thêm ảnh.", true);
        }
        if ((latitude == null) != (longitude == null)) {
            throw new DispatchRuleException("latitude", "Tọa độ phải có cả vĩ độ và kinh độ.");
        }
        Staff uploader = actor(username);
        String code = documentType == null ? INCIDENT_PHOTO : documentType;
        if (!INCIDENT_PHOTO.equals(code)
                && !("VET_REPORT".equals(code) && "COORDINATOR".equals(uploader.getUser().getRole().getCode()))) {
            throw new DispatchRuleException("documentType", "Loại tệp chứng cứ không phù hợp.");
        }
        Long id = documentFiles.attach(file, code, DocumentSubject.INCIDENT, incident.getId(), uploader.getUserId());
        Document photo = documents.findById(id).orElseThrow();
        photo.setCapturedAt(clock.instant());
        photo.setLatitude(latitude);
        photo.setLongitude(longitude);
        return photos(incidentId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvidenceResponseDto> photos(Long incidentId) {
        if (!incidents.existsById(incidentId)) throw new ResourceNotFoundException("Không tìm thấy sự cố " + incidentId);
        return describe(documents.findByIncident_IdOrderById(incidentId));
    }

    private List<EvidenceResponseDto> describe(List<Document> found) {
        Map<Long, String> names = staff.findAllById(found.stream().map(d -> d.getUploadedBy().getId()).distinct().toList()).stream()
                .collect(Collectors.toMap(Staff::getUserId, Staff::getFullName, (a, b) -> a));
        return found.stream().map(d -> EvidenceResponseDto.from(d, names.getOrDefault(d.getUploadedBy().getId(), d.getUploadedBy().getUsername()))).toList();
    }

    private Staff actor(String username) {
        return staff.findByUser_Username(username).orElseThrow(() -> new AccessDeniedException("Không tìm thấy hồ sơ nhân viên."));
    }
}
