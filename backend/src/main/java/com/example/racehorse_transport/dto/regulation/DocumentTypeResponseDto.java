package com.example.racehorse_transport.dto.regulation;

import com.example.racehorse_transport.entity.DocumentType;
import com.example.racehorse_transport.entity.enums.DocumentSubject;
import java.util.List;

public record DocumentTypeResponseDto(
        Long id, String code, String name, DocumentSubject ownerType) {

    public static DocumentTypeResponseDto from(DocumentType entity) {
        return new DocumentTypeResponseDto(entity.getId(), entity.getCode(), entity.getName(), entity.getOwnerType());
    }

    public static List<DocumentTypeResponseDto> fromList(List<DocumentType> entities) {
        return entities.stream().map(DocumentTypeResponseDto::from).toList();
    }
}
