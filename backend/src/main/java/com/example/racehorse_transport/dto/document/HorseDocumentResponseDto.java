package com.example.racehorse_transport.dto.document;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response tra ve sau khi upload hoac review ho so ngua.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "HorseDocumentResponse")
public class HorseDocumentResponseDto {

    private Integer id;
    private Integer horseId;
    private String horseName;
    private String documentType;
    private String fileUrl;
    /**
     * Trang thai ho so:
     * PENDING  - Cho duyet
     * APPROVED - Da duyet
     * REJECTED - Tu choi
     */
    private String status;

    private Instant uploadDate;

    /** Ghi chu tu chuyen vien (neu co) */
    private String reviewNote;

    /** Ten chuyen vien da duyet (neu co) */
    private String reviewerName;

    /** Thoi diem duyet / tu choi (neu co) */
    private Instant reviewedAt;
}
