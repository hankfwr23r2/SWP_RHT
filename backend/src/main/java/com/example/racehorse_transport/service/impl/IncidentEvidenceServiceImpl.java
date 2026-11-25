package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.incident.EvidenceResponseDto;
import com.example.racehorse_transport.entity.CostItem;
import com.example.racehorse_transport.entity.Document;
import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.CostCategory;
import com.example.racehorse_transport.entity.enums.CrewAssignmentStatus;
import com.example.racehorse_transport.entity.enums.CrewRole;
import com.example.racehorse_transport.entity.enums.DocumentSubject;
import com.example.racehorse_transport.entity.enums.IncidentStatus;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.IncidentPolicy;
import com.example.racehorse_transport.repository.CostItemRepository;
import com.example.racehorse_transport.repository.CrewAssignmentRepository;
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
    private static final String VEHICLE_SWAP_RECORD = "VEHICLE_SWAP_RECORD";

    private final DocumentService documentFiles;
    private final DocumentRepository documents;
    private final CostItemRepository costs;
    private final IncidentRepository incidents;
    private final StaffRepository staff;
    private final CrewAssignmentRepository crew;
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
        Incident incident = incidents.findForUpdate(incidentId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự cố " + incidentId));
        if (incident.getStatus() == IncidentStatus.CLOSED) {
            throw new DispatchRuleException("incidentId", "Sự cố đã đóng, không thêm ảnh.", true);
        }
        if ((latitude == null) != (longitude == null)) {
            throw new DispatchRuleException("latitude", "Tọa độ phải có cả vĩ độ và kinh độ.");
        }
        Staff uploader = actor(username);
        String code = documentType == null ? INCIDENT_PHOTO : documentType;
        String role = uploader.getUser().getRole().getCode();
        if (VEHICLE_SWAP_RECORD.equals(code)) {
            requireVehicleSwapRecordUploader(incident, uploader, role);
            requireImage(file, "Chọn ảnh biên bản đổi xe đã ký.");
        } else if (FIT_CHECK_PHOTO.equals(code)) {
            requireFitCheckPhotoUploader(incident, username, role);
            IncidentPolicy.requireFitConfirmation(incident.getIncidentType(), incident.getHorseLeg() != null);
            requireImage(file, "Chọn ảnh con ngựa để xác nhận đủ sức.");
        } else if (!INCIDENT_PHOTO.equals(code) && !("VET_REPORT".equals(code) && "COORDINATOR".equals(role))) {
            throw new DispatchRuleException("documentType", "Loại tệp chứng cứ không phù hợp.");
        }
        Long id = documentFiles.attach(file, code, DocumentSubject.INCIDENT, incident.getId(), uploader.getUserId());
        Document photo = documents.findById(id).orElseThrow();
        photo.setCapturedAt(clock.instant());
        photo.setLatitude(latitude);
        photo.setLongitude(longitude);
        return photos(incidentId);
    }

    private void requireVehicleSwapRecordUploader(Incident incident, Staff uploader, String role) {
        VehicleTrip trip = tripOf(incident);
        if (!("DRIVER".equals(role) || "ESCORT".equals(role)) || trip == null
                || !crew.existsByVehicleTrip_IdAndStaff_UserIdAndStatusNot(trip.getId(), uploader.getUserId(), CrewAssignmentStatus.RELEASED)) {
            throw new AccessDeniedException("Chỉ tổ lái của lượt xe được tải biên bản đổi xe.");
        }
    }

    private void requireFitCheckPhotoUploader(Incident incident, String username, String role) {
        VehicleTrip trip = tripOf(incident);
        if (!"ESCORT".equals(role) || trip == null || crew.findFirstByVehicleTrip_IdAndStaff_User_UsernameAndStatusNot(
                trip.getId(), username, CrewAssignmentStatus.RELEASED)
                .filter(assignment -> assignment.getCrewRole() == CrewRole.ESCORT).isEmpty()) {
            throw new AccessDeniedException("Chỉ hộ tống của lượt xe được tải ảnh xác nhận đủ sức.");
        }
    }

    private VehicleTrip tripOf(Incident incident) {
        return incident.getVehicleTrip() != null ? incident.getVehicleTrip()
                : incident.getHorseLeg() == null ? null : incident.getHorseLeg().getVehicleTrip();
    }

    private void requireImage(MultipartFile file, String message) {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new DispatchRuleException("file", message);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void requireVehicleSwapRecord(Long incidentId) {
        if (!incidents.hasDocumentType(incidentId, VEHICLE_SWAP_RECORD)) {
            throw new DispatchRuleException("incidentId",
                    "Chưa có biên bản đổi xe. Tổ xe chụp biên bản đã ký trước khi đổi xe.", true);
        }
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
