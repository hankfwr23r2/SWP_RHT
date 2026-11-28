package com.example.racehorse_transport.dto.booking;

import com.example.racehorse_transport.entity.Document;
import com.example.racehorse_transport.entity.DocumentRequirement;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

public record DocumentUploadRequestDto(@NotNull @Positive Long requirementId, @Positive Long bookingHorseId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate, @NotNull MultipartFile file) {
    public void applyTo(Document document, DocumentRequirement requirement) {
        document.setDocumentRequirement(requirement);
        document.setExpiryDate(expiryDate);
    }
}
