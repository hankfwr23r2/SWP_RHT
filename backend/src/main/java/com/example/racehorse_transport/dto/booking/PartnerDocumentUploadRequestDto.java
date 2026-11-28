package com.example.racehorse_transport.dto.booking;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

public record PartnerDocumentUploadRequestDto(@NotNull @Positive Long requirementId, @Positive Long bookingHorseId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate, @NotNull MultipartFile file) {
    public DocumentUploadRequestDto toUploadRequest() {
        return new DocumentUploadRequestDto(requirementId, bookingHorseId, expiryDate, file);
    }
}
