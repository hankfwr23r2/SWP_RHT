package com.example.racehorse_transport.dto.incident;

import com.example.racehorse_transport.entity.Document;
import java.math.BigDecimal;
import java.time.Instant;

// Chứng từ của sự cố (B4): biên lai, hóa đơn của chi phí hoặc ảnh hiện trường; capturedAt và tọa độ chỉ có ở ảnh chụp tại chỗ
public record EvidenceResponseDto(Long id, String documentTypeCode, String documentTypeName, String fileName, String fileUrl,
                                  String uploadedByName, Instant uploadedAt, Instant capturedAt, BigDecimal latitude, BigDecimal longitude) {

    public static EvidenceResponseDto from(Document document, String uploadedByName) {
        return new EvidenceResponseDto(document.getId(), document.getDocumentType().getCode(), document.getDocumentType().getName(),
                document.getFileName(), document.getFileUrl(), uploadedByName, document.getUploadedAt(), document.getCapturedAt(),
                document.getLatitude(), document.getLongitude());
    }
}
