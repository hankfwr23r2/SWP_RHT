package com.example.racehorse_transport.dto.booking;

import com.example.racehorse_transport.entity.Document;
import com.example.racehorse_transport.entity.enums.DocumentStatus;
import java.time.Instant;
import java.time.LocalDate;

public record BookingDocumentResponseDto(Long id, Long requirementId, String typeCode, Long horseId,
        Long bookingHorseId, String fileName, String fileUrl, DocumentStatus status, LocalDate expiryDate,
        Instant reviewedAt, String reviewNote) {

    public static BookingDocumentResponseDto from(Document row) {
        return from(row, row.getBookingHorse() == null ? null : row.getBookingHorse().getId());
    }

    // Giấy gắn với hồ sơ ngựa: service cho biết đó là con ngựa nào trong đơn
    public static BookingDocumentResponseDto from(Document row, Long bookingHorseId) {
        Long requirementId = row.getDocumentRequirement() == null ? null : row.getDocumentRequirement().getId();
        return new BookingDocumentResponseDto(row.getId(), requirementId,
                row.getDocumentType().getCode(), row.getHorse() == null ? null : row.getHorse().getId(),
                bookingHorseId,
                row.getFileName(), row.getFileUrl(), row.getStatus(), row.getExpiryDate(),
                row.getReviewedAt(), row.getReviewNote());
    }
}
