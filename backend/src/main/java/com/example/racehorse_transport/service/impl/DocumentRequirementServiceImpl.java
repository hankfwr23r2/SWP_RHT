package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.DocumentRequirementRequest;
import com.example.racehorse_transport.dto.DocumentRequirementResponse;
import com.example.racehorse_transport.entity.DocumentRequirement;
import com.example.racehorse_transport.entity.DocumentRequirementChangeLog;
import com.example.racehorse_transport.repository.DocumentRequirementChangeLogRepository;
import com.example.racehorse_transport.repository.DocumentRequirementRepository;
import com.example.racehorse_transport.service.DocumentRequirementService;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentRequirementServiceImpl implements DocumentRequirementService {
    private final DocumentRequirementRepository repository;
    private final DocumentRequirementChangeLogRepository changeLogRepository;

    public DocumentRequirementServiceImpl(
            DocumentRequirementRepository repository,
            DocumentRequirementChangeLogRepository changeLogRepository) {
        this.repository = repository;
        this.changeLogRepository = changeLogRepository;
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
        logAllFields(saved, actor);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public DocumentRequirementResponse update(Integer id, DocumentRequirementRequest request, String actor) {
        validateScope(request);
        DocumentRequirement requirement = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cấu hình giấy tờ."));
        ensureUnique(request, id);
        Instant changedAt = Instant.now();
        logChanges(requirement, request, actor, changedAt);
        copy(request, requirement);
        requirement.setUpdatedAt(changedAt);
        requirement.setUpdatedBy(actor);
        return toResponse(repository.save(requirement));
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
            logChange(requirement.getId(), "status", requirement.getStatus(), status, actor, changedAt);
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

    private void logAllFields(DocumentRequirement value, String actor) {
        Instant now = Instant.now();
        logChange(value.getId(), "created", null,
                value.getDocumentGroup() + "|" + value.getDocumentType() + "|" + value.getDocumentName()
                        + "|" + value.getTripType() + "|" + value.getCountry() + "|" + value.isRequired()
                        + "|" + value.getStatus(),
                actor, now);
    }

    private void logChanges(
            DocumentRequirement current, DocumentRequirementRequest next, String actor, Instant changedAt) {
        logIfChanged(current.getId(), "documentGroup", current.getDocumentGroup(), next.getDocumentGroup(), actor, changedAt);
        logIfChanged(current.getId(), "documentType", current.getDocumentType(), next.getDocumentType(), actor, changedAt);
        logIfChanged(current.getId(), "documentName", current.getDocumentName(), next.getDocumentName().trim(), actor, changedAt);
        logIfChanged(current.getId(), "description", current.getDescription(), next.getDescription(), actor, changedAt);
        logIfChanged(current.getId(), "tripType", current.getTripType(), next.getTripType(), actor, changedAt);
        logIfChanged(current.getId(), "country", current.getCountry(), next.getCountry(), actor, changedAt);
        logIfChanged(current.getId(), "required", String.valueOf(current.isRequired()), String.valueOf(next.isRequired()), actor, changedAt);
        logIfChanged(current.getId(), "status", current.getStatus(), next.getStatus(), actor, changedAt);
    }

    private void logIfChanged(
            Integer id, String field, String oldValue, String newValue, String actor, Instant changedAt) {
        if (!Objects.equals(oldValue, newValue)) {
            logChange(id, field, oldValue, newValue, actor, changedAt);
        }
    }

    private void logChange(
            Integer id, String field, String oldValue, String newValue, String actor, Instant changedAt) {
        DocumentRequirementChangeLog entry = new DocumentRequirementChangeLog();
        entry.setRequirementId(id);
        entry.setFieldName(field);
        entry.setOldValue(oldValue);
        entry.setNewValue(newValue);
        entry.setChangedBy(actor);
        entry.setChangedAt(changedAt);
        changeLogRepository.save(entry);
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
