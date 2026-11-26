package com.example.racehorse_transport.dto.regulation;

import com.example.racehorse_transport.entity.DocumentRequirement;
import com.example.racehorse_transport.entity.DocumentType;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.enums.DocumentSubject;
import com.example.racehorse_transport.entity.enums.DocumentSubmitter;
import java.util.List;

public record DocumentRequirementResponseDto(
        Long id, Long countryRuleId, Long documentTypeId, String documentTypeCode, String documentTypeName,
        DocumentSubject ownerType, Long gateLocationId, DocumentSubmitter submitterType, Boolean mandatory) {

    public static DocumentRequirementResponseDto from(DocumentRequirement entity) {
        DocumentType type = entity.getDocumentType();
        Location gate = entity.getGateLocation();
        return new DocumentRequirementResponseDto(entity.getId(), entity.getRule().getId(), type.getId(), type.getCode(), type.getName(),
                type.getOwnerType(), gate == null ? null : gate.getId(), entity.getSubmitterType(), entity.getMandatory());
    }

    public static List<DocumentRequirementResponseDto> fromList(List<DocumentRequirement> entities) {
        return entities.stream().map(DocumentRequirementResponseDto::from).toList();
    }
}
