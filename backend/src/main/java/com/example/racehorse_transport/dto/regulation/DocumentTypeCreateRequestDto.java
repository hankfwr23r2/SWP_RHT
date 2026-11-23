package com.example.racehorse_transport.dto.regulation;

import com.example.racehorse_transport.entity.DocumentType;
import com.example.racehorse_transport.entity.enums.DocumentSubject;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DocumentTypeCreateRequestDto(
        @NotBlank @Size(max = 30) String code,
        @NotBlank @Size(max = 200) String name,
        @NotNull DocumentSubject ownerType) {

    public DocumentType toEntity() {
        DocumentType entity = new DocumentType();
        entity.setCode(code);
        entity.setName(name);
        entity.setOwnerType(ownerType);
        return entity;
    }
}
