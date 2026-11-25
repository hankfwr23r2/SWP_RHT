package com.example.racehorse_transport.dto.booking;

import com.example.racehorse_transport.entity.Document;
import jakarta.validation.constraints.NotBlank;

public record DocumentRevocationRequestDto(@NotBlank(message = "Phải nhập lý do.") String reason) {
    public void applyTo(Document document) {
        document.setReviewNote(reason.strip());
    }
}
