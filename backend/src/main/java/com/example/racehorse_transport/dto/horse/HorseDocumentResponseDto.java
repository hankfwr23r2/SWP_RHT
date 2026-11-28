package com.example.racehorse_transport.dto.horse;

import com.example.racehorse_transport.entity.Document;
import com.example.racehorse_transport.entity.DocumentType;
import com.example.racehorse_transport.entity.enums.DocumentStatus;
import java.time.Instant;
import java.time.LocalDate;

public record HorseDocumentResponseDto(String documentTypeCode, String documentTypeName,
        Long id, String fileName, String fileUrl, DocumentStatus status, String reviewNote,
        Instant uploadedAt, LocalDate expiryDate, boolean canUpload) {

    public static HorseDocumentResponseDto from(DocumentType type, Document latest, DocumentStatus status, boolean canUpload) {
        if (latest == null) {
            return new HorseDocumentResponseDto(type.getCode(), type.getName(), null, null, null, null, null, null, null, canUpload);
        }
        return new HorseDocumentResponseDto(type.getCode(), type.getName(), latest.getId(), latest.getFileName(), latest.getFileUrl(),
                status, latest.getReviewNote(), latest.getUploadedAt(), latest.getExpiryDate(), canUpload);
    }
}
