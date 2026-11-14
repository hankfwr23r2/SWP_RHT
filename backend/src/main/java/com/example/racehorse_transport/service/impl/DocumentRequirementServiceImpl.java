package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.service.AdminAuditWriter;
import static com.example.racehorse_transport.service.AdminAuditWriter.values;
import com.example.racehorse_transport.dto.DocumentRequirementRequest;
import com.example.racehorse_transport.dto.DocumentRequirementResponse;
import com.example.racehorse_transport.entity.DocumentRequirement;
import com.example.racehorse_transport.repository.DocumentRequirementRepository;
import com.example.racehorse_transport.service.DocumentRequirementService;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentRequirementServiceImpl implements DocumentRequirementService {
    private final AdminAuditWriter audit;

    private final DocumentRequirementRepository repository;

    public DocumentRequirementServiceImpl(
            DocumentRequirementRepository repository,
            AdminAuditWriter audit) {
        this.audit = audit;
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentRequirementResponse> listAll() {
        return repository.findAll().stream()
                .sorted((a, b) -> {
                    int group = a.getDocumentGroup().compareTo(b.getDocumentGroup());
                    return group != 0 ? group : a.getDocumentName().compareToIgnoreCase(b.getDocumentName());
                })
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentRequirementResponse> listActive() {
        return repository.findByStatusOrderByDocumentGroupAscDocumentNameAsc("ACTIVE").stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public DocumentRequirementResponse create(DocumentRequirementRequest request, String actor) {
        validateScope(request);
        ensureUnique(request, null);
        DocumentRequirement requirement = new DocumentRequirement();
        copy(request, requirement);
        requirement.setUpdatedAt(Instant.now());
        requirement.setUpdatedBy(actor);
        DocumentRequirement saved = repository.save(requirement);
        audit.record("CREATE_DOCUMENT_REQUIREMENT", "DOCUMENT_REQUIREMENT", saved.getId(), saved.getDocumentName(),
                "Tạo quy định giấy tờ", null, snapshot(saved));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public DocumentRequirementResponse update(Integer id, DocumentRequirementRequest request, String actor) {
        validateScope(request);
        DocumentRequirement requirement = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cấu hình giấy tờ."));
        ensureUnique(request, id);
        var before = snapshot(requirement);
        Instant changedAt = Instant.now();
        copy(request, requirement);
        requirement.setUpdatedAt(changedAt);
        requirement.setUpdatedBy(actor);
        DocumentRequirement saved = repository.save(requirement);
        audit.record("UPDATE_DOCUMENT_REQUIREMENT", "DOCUMENT_REQUIREMENT", id, saved.getDocumentName(),
                "Cập nhật quy định giấy tờ", before, snapshot(saved));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public DocumentRequirementResponse changeStatus(Integer id, String status, String actor) {
        if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status)) {
            throw new IllegalArgumentException("Trạng thái phải là ACTIVE hoặc INACTIVE.");
        }
        DocumentRequirement requirement = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cấu hình giấy tờ."));
        if (!Objects.equals(requirement.getStatus(), status)) {
            Instant changedAt = Instant.now();
            audit.record("CHANGE_DOCUMENT_REQUIREMENT_STATUS", "DOCUMENT_REQUIREMENT", id, requirement.getDocumentName(),
                    "Đổi trạng thái quy định giấy tờ", values("status", requirement.getStatus()), values("status", status));
            requirement.setStatus(status);
            requirement.setUpdatedAt(changedAt);
            requirement.setUpdatedBy(actor);
            repository.save(requirement);
        }
        return toResponse(requirement);
    }

    private void ensureUnique(DocumentRequirementRequest request, Integer ignoredId) {
        boolean duplicate = repository.findAll().stream()
                .anyMatch(item -> !Objects.equals(item.getId(), ignoredId)
                        && request.getDocumentGroup().equals(item.getDocumentGroup())
                        && request.getDocumentType().equals(item.getDocumentType())
                        && request.getTripType().equals(item.getTripType())
                        && request.getCountry().equals(item.getCountry()));
        if (duplicate) {
            throw new IllegalArgumentException("Cấu hình loại giấy, loại chuyến và quốc gia này đã tồn tại.");
        }
    }

    private void validateScope(DocumentRequirementRequest request) {
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

    private void copy(DocumentRequirementRequest source, DocumentRequirement target) {
        target.setDocumentGroup(source.getDocumentGroup());
        target.setDocumentType(source.getDocumentType());
        target.setDocumentName(source.getDocumentName().trim());
        target.setDescription(source.getDescription() == null ? null : source.getDescription().trim());
        target.setTripType(source.getTripType());
        target.setCountry(source.getCountry());
        target.setRequired(source.isRequired());
        target.setStatus(source.getStatus());
    }

    private java.util.Map<String, Object> snapshot(DocumentRequirement r) {
        return values("documentGroup", r.getDocumentGroup(), "documentType", r.getDocumentType(),
                "documentName", r.getDocumentName(), "description", r.getDescription(), "tripType", r.getTripType(),
                "country", r.getCountry(), "required", r.isRequired(), "status", r.getStatus());
    }

    private DocumentRequirementResponse toResponse(DocumentRequirement entity) {
        return DocumentRequirementResponse.builder()
                .id(entity.getId())
                .documentGroup(entity.getDocumentGroup())
                .documentType(entity.getDocumentType())
                .documentName(entity.getDocumentName())
                .description(entity.getDescription())
                .tripType(entity.getTripType())
                .country(entity.getCountry())
                .required(entity.isRequired())
                .status(entity.getStatus())
                .updatedBy(entity.getUpdatedBy())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
