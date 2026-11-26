package com.example.racehorse_transport.dto.regulation;

import com.example.racehorse_transport.entity.CountryRule;
import com.example.racehorse_transport.entity.DocumentRequirement;
import com.example.racehorse_transport.entity.DocumentType;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.enums.DocumentSubmitter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DocumentRequirementCreateRequestDto(
        @NotNull @Positive Long documentTypeId,
        @Positive Long gateLocationId,
        @NotNull DocumentSubmitter submitterType,
        @NotNull Boolean mandatory) {

    public DocumentRequirement toEntity(CountryRule rule, DocumentType documentType, Location gateLocation) {
        DocumentRequirement entity = new DocumentRequirement();
        entity.setRule(rule);
        entity.setDocumentType(documentType);
        entity.setGateLocation(gateLocation);
        entity.setSubmitterType(submitterType);
        entity.setMandatory(mandatory);
        return entity;
    }
}
