package com.example.racehorse_transport.dto.document;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request khi Chuyen vien duyet hoac tu choi ho so ngua.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ReviewHorseDocumentRequest")
public class ReviewHorseDocumentRequestDto {

    /**
     * true  = APPROVED (Duyet ho so)
     * false = REJECTED (Tu choi ho so)
     */
    private boolean approved;

    /** Ghi chu cua chuyen vien (ly do tu choi, yeu cau bo sung...) */
    private String note;

    /** Ten chuyen vien thuc hien duyet (de ghi log) */
    private String reviewerName;
}
