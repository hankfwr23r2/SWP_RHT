package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.DocumentRequirementRequestDto;
import com.example.racehorse_transport.dto.DocumentRequirementResponseDto;
import com.example.racehorse_transport.entity.DocumentRequirement;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.repository.DocumentRequirementRepository;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.service.AdminAuditService;
import com.example.racehorse_transport.service.DocumentRequirementService;
import com.example.racehorse_transport.util.DocumentRequirementPolicy;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.example.racehorse_transport.service.AdminAuditService.values;

@Service
public class DocumentRequirementServiceImpl implements DocumentRequirementService {
    private final AdminAuditService audit;

    private final DocumentRequirementRepository repository;
    private final StaffRepository staffRepository;
    private final LocationRepository locationRepository;

    public DocumentRequirementServiceImpl(
            DocumentRequirementRepository repository,
            StaffRepository staffRepository,
            LocationRepository locationRepository,
            AdminAuditService audit) {
        this.audit = audit;
        this.repository = repository;
        this.staffRepository = staffRepository;
        this.locationRepository = locationRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentRequirementResponseDto> listAll() {
        return repository.findAllWithUpdatedBy().stream()
                .sorted((a, b) -> {
                    int group = a.getDocumentGroup().compareTo(b.getDocumentGroup());
                    return group != 0 ? group : a.getDocumentName().compareToIgnoreCase(b.getDocumentName());
                })
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentRequirementResponseDto> listActive() {
        return repository.findByStatusOrderByDocumentGroupAscDocumentNameAsc("ACTIVE").stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public DocumentRequirementResponseDto create(DocumentRequirementRequestDto request, String actor) {
        validateScope(request);
        Location gate = resolveGate(request);
        ensureUnique(request, null);
        Staff staff = resolveStaff(actor);
        DocumentRequirement requirement = request.toEntity(gate);
        requirement.setUpdatedAt(Instant.now());
        requirement.setUpdatedBy(staff);
        DocumentRequirement saved = repository.save(requirement);
        audit.record("CREATE_DOCUMENT_REQUIREMENT", "DOCUMENT_REQUIREMENT", saved.getId(), saved.getDocumentName(),
                "Tạo quy định giấy tờ", null, snapshot(saved));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public DocumentRequirementResponseDto update(Integer id, DocumentRequirementRequestDto request, String actor) {
        validateScope(request);
        Location gate = resolveGate(request);
        DocumentRequirement requirement = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cấu hình giấy tờ."));
        ensureUnique(request, id);
        Staff staff = resolveStaff(actor);
        var before = snapshot(requirement);
        Instant changedAt = Instant.now();
        request.applyTo(requirement, gate);
        requirement.setUpdatedAt(changedAt);
        requirement.setUpdatedBy(staff);
        DocumentRequirement saved = repository.save(requirement);
        audit.record("UPDATE_DOCUMENT_REQUIREMENT", "DOCUMENT_REQUIREMENT", id, saved.getDocumentName(),
                "Cập nhật quy định giấy tờ", before, snapshot(saved));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public DocumentRequirementResponseDto changeStatus(Integer id, String status, String actor) {
        if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status)) {
            throw new IllegalArgumentException("Trạng thái phải là ACTIVE hoặc INACTIVE.");
        }
        DocumentRequirement requirement = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cấu hình giấy tờ."));
        if (!Objects.equals(requirement.getStatus(), status)) {
            Staff staff = resolveStaff(actor);
            Instant changedAt = Instant.now();
            audit.record("CHANGE_DOCUMENT_REQUIREMENT_STATUS", "DOCUMENT_REQUIREMENT", id, requirement.getDocumentName(),
                    "Đổi trạng thái quy định giấy tờ", values("status", requirement.getStatus()), values("status", status));
            requirement.setStatus(status);
            requirement.setUpdatedAt(changedAt);
            requirement.setUpdatedBy(staff);
            repository.save(requirement);
        }
        return toResponse(requirement);
    }

    // Chỉ ADMIN (có bản ghi STAFF) được sửa cấu hình giấy tờ
    private Staff resolveStaff(String username) {
        return staffRepository.findByUser_Username(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhân viên thực hiện thao tác."));
    }

    private void ensureUnique(DocumentRequirementRequestDto request, Integer ignoredId) {
        boolean duplicate = repository.findAll().stream()
                .anyMatch(item -> !Objects.equals(item.getId(), ignoredId)
                        && request.getDocumentGroup().equals(item.getDocumentGroup())
                        && request.getDocumentType().equals(item.getDocumentType())
                        && request.getTripType().equals(item.getTripType())
                        && request.getCountry().equals(item.getCountry())
                        && Objects.equals(request.getLocationId(), locationId(item)));
        if (duplicate) {
            throw new IllegalArgumentException("Cấu hình loại giấy, loại chuyến, quốc gia và cửa khẩu này đã tồn tại.");
        }
    }

    // Giấy tờ theo cửa khẩu: chỉ nhóm thông quan, chuyến quốc tế, địa điểm phải là BorderGate dẫn tới đúng quốc gia
    private Location resolveGate(DocumentRequirementRequestDto request) {
        if (request.getLocationId() == null) {
            return null;
        }
        if (!"CLEARANCE".equals(request.getDocumentGroup())) {
            throw new IllegalArgumentException("Chỉ giấy tờ thông quan mới gắn được cửa khẩu.");
        }
        if (!"INTERNATIONAL".equals(request.getTripType())) {
            throw new IllegalArgumentException("Giấy tờ theo cửa khẩu chỉ áp dụng cho chuyến quốc tế.");
        }
        Location gate = locationRepository.findById(request.getLocationId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cửa khẩu."));
        if (!"BorderGate".equalsIgnoreCase(gate.getLocationType())) {
            throw new IllegalArgumentException("Địa điểm được chọn không phải cửa khẩu.");
        }
        String gateCountry = gate.getDestinationCountry();
        if (gateCountry != null && !gateCountry.isBlank() && !"ALL".equals(request.getCountry())
                && !DocumentRequirementPolicy.normalizeCountry(gateCountry).equals(request.getCountry())) {
            throw new IllegalArgumentException("Cửa khẩu không dẫn tới quốc gia đã chọn.");
        }
        return gate;
    }

    private static Integer locationId(DocumentRequirement requirement) {
        return requirement.getLocation() == null ? null : requirement.getLocation().getId();
    }

    private void validateScope(DocumentRequirementRequestDto request) {
        if ("DOMESTIC".equals(request.getTripType())
                && !List.of("ALL", "VN").contains(request.getCountry())) {
            throw new IllegalArgumentException("Chuyến trong nước chỉ áp dụng cho Việt Nam hoặc tất cả quốc gia.");
        }
        if ("HORSE".equals(request.getDocumentGroup())) {
            if (!List.of("passport", "vaccine", "lab").contains(request.getDocumentType())) {
                throw new IllegalArgumentException("Loại giấy này không thuộc nhóm hồ sơ ngựa.");
            }
        } else if (List.of("passport", "vaccine", "lab").contains(request.getDocumentType())) {
            throw new IllegalArgumentException("Giấy tờ hồ sơ ngựa phải thuộc nhóm hồ sơ ngựa.");
        } else if (List.of("customs_declaration", "import_permit", "quarantine_cert", "ata_carnet", "commercial_invoice")
                .contains(request.getDocumentType()) && !"INTERNATIONAL".equals(request.getTripType())) {
            throw new IllegalArgumentException("Loại giấy này chỉ áp dụng cho chuyến quốc tế.");
        }
    }


    private java.util.Map<String, Object> snapshot(DocumentRequirement r) {
        return values("documentGroup", r.getDocumentGroup(), "documentType", r.getDocumentType(),
                "documentName", r.getDocumentName(), "description", r.getDescription(), "tripType", r.getTripType(),
                "country", r.getCountry(), "locationId", locationId(r), "required", r.isRequired(), "status", r.getStatus());
    }

    private DocumentRequirementResponseDto toResponse(DocumentRequirement entity) {
        return DocumentRequirementResponseDto.builder()
                .id(entity.getId())
                .documentGroup(entity.getDocumentGroup())
                .documentType(entity.getDocumentType())
                .documentName(entity.getDocumentName())
                .description(entity.getDescription())
                .tripType(entity.getTripType())
                .country(entity.getCountry())
                .locationId(locationId(entity))
                .locationName(entity.getLocation() == null ? null : entity.getLocation().getLocationName())
                .required(entity.isRequired())
                .status(entity.getStatus())
                .updatedBy(entity.getUpdatedBy() == null ? "SYSTEM" : entity.getUpdatedBy().getUser().getUsername())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
